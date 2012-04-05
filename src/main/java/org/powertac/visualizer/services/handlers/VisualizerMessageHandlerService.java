package org.powertac.visualizer.services.handlers;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.SortedSet;

import org.apache.log4j.Logger;
import org.powertac.common.BalancingTransaction;
import org.powertac.common.BankTransaction;
import org.powertac.common.CashPosition;
import org.powertac.common.ClearedTrade;
import org.powertac.common.Competition;
import org.powertac.common.DistributionTransaction;
import org.powertac.common.MarketPosition;
import org.powertac.common.MarketTransaction;
import org.powertac.common.Order;
import org.powertac.common.Orderbook;
import org.powertac.common.OrderbookOrder;
import org.powertac.common.PluginConfig;
import org.powertac.common.Rate;
import org.powertac.common.TariffSpecification;
import org.powertac.common.TariffTransaction;
import org.powertac.common.WeatherForecast;
import org.powertac.common.WeatherReport;
import org.powertac.common.msg.CustomerBootstrapData;
import org.powertac.common.msg.DistributionReport;
import org.powertac.common.msg.SimPause;
import org.powertac.common.msg.SimResume;
import org.powertac.common.msg.SimStart;
import org.powertac.common.msg.TimeslotComplete;
import org.powertac.common.msg.TimeslotUpdate;
import org.powertac.visualizer.MessageDispatcher;
import org.powertac.visualizer.SpringApplicationContext;
import org.powertac.visualizer.beans.AppearanceListBean;
import org.powertac.visualizer.beans.VisualizerBean;
import org.powertac.visualizer.domain.broker.BrokerModel;
import org.powertac.visualizer.domain.customer.Customer;
import org.powertac.visualizer.domain.wholesale.WholesaleMarket;
import org.powertac.visualizer.domain.wholesale.WholesaleSnapshot;
import org.powertac.visualizer.interfaces.Initializable;
import org.powertac.visualizer.interfaces.TimeslotCompleteActivation;
import org.powertac.visualizer.interfaces.VisualBroker;
import org.powertac.visualizer.services.WholesaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class VisualizerMessageHandlerService implements Initializable {

	private Logger log = Logger.getLogger(VisualizerMessageHandlerService.class);
	@Autowired
	private VisualizerBean visualizerBean;
	@Autowired
	private VisualizerMessageHandlerHelperService helper;
	@Autowired
	private AppearanceListBean appearanceListBean;
	@Autowired
	private MessageDispatcher router;
	private int firstTimeslotIndex=-1;

	public void handleMessage(Competition competition) {
			visualizerBean.setCompetition(competition);		
	}

	public void handleMessage(TimeslotUpdate timeslotUpdate) {
		visualizerBean.setTimeslotUpdate(timeslotUpdate);

		// for the first timeslot:
		if (visualizerBean.getFirstTimeslotInstant() == null)
			visualizerBean.setFirstTimeslotInstant(timeslotUpdate.getPostedTime());

		StringBuilder builder = new StringBuilder();
		builder.append("Enabled timeslots:");
		for (int i = timeslotUpdate.getFirstEnabled(); i < timeslotUpdate.getLastEnabled(); i++) {

			builder.append(" ").append(i);
		}

		log.debug(builder + "\n");

		int relativeTimeslotIndex = helper.computeRelativeTimeslotIndex(timeslotUpdate.getPostedTime());
		// for all brokers and gencos:
		// helper.updateTimeslotIndex(relativeTimeslotIndex);
		// update for visualizerBean:
		visualizerBean.setRelativeTimeslotIndex(relativeTimeslotIndex);

		log.info("\nTimeslot index: " + relativeTimeslotIndex + "\nPostedtime:" + timeslotUpdate.getPostedTime());

		Competition comp = visualizerBean.getCompetition();
		// timeslot serial number:
		visualizerBean.setCurrentTimeslotSerialNumber(timeslotUpdate.getFirstEnabled()
				- comp.getDeactivateTimeslotsAhead());
	}

	public void handleMessage(TimeslotComplete complete) {
		
		if(firstTimeslotIndex==-1){
			firstTimeslotIndex = complete.getTimeslotIndex();
		}
		
		// activate beans that implement timeslotcompleteactivation interface:
		List<TimeslotCompleteActivation> activators = SpringApplicationContext
				.listBeansOfType(TimeslotCompleteActivation.class);
		for (TimeslotCompleteActivation active : activators) {
			log.debug("activating..."+active.getClass().getSimpleName());
			active.activate(complete.getTimeslotIndex());
		}

		// new day? if so, make new day overview:
		
		int relativeTimeslotIndex = complete.getTimeslotIndex()-firstTimeslotIndex;
		
		if (relativeTimeslotIndex % 23 == 0 && firstTimeslotIndex!=complete.getTimeslotIndex()) {
			helper.buildDayOverview();
		}

	}

	public void handleMessage(SimStart simStart) {
		log.info("\nINSTANT: " + simStart.getStart());
		// TODO

	}

	public void handleMessage(SimPause simPause) {
		// TODO
	}

	public void handleMessage(WeatherReport weatherReport) {

		if (weatherReport.getCurrentTimeslot() != null) {
			log.debug("\nStart Instant: " + weatherReport.getCurrentTimeslot().getStartInstant());
			visualizerBean.setWeatherReport(weatherReport);
		} else {
			log.warn("Timeslot for Weather report object is null!!");
		}

		log.debug("CLOUD COVER: " + weatherReport.getCloudCover() + " TEMP: " + weatherReport.getTemperature()
				+ "W DIR:" + weatherReport.getWindDirection() + "W SPEED:" + weatherReport.getWindSpeed());

	}

	public void handleMessage(WeatherForecast weatherForecast) {
		log.debug("\nCurrent timeslot:\n Serial number: " + weatherForecast.getCurrentTimeslot().getSerialNumber()
				+ " ID:" + weatherForecast.getCurrentTimeslot().getId() + " Start instant: "
				+ weatherForecast.getCurrentTimeslot().getStartInstant());
		visualizerBean.setWeatherForecast(weatherForecast);
		weatherForecast.getPredictions();
		// TODO !!!!!!!!!! FORECAST
	}

	
	public void handleMessage(BankTransaction bankTransaction) {
		// TODO
	}

	
	public void handleMessage(MarketTransaction marketTransaction) {
		log.debug("\nBroker: " + marketTransaction.getBroker().getUsername() + " MWh: " + marketTransaction.getMWh()
				+ "\n Price: " + marketTransaction.getPrice() + " Postedtime: " + marketTransaction.getPostedTime()
				+ " Timeslot\n Serial Number: " + marketTransaction.getTimeslot().getSerialNumber() + " Index: "
				+ helper.computeRelativeTimeslotIndex(marketTransaction.getTimeslot().getStartInstant()));

		// TODO

	}

	public void handleMessage(MarketPosition marketPosition) {

		log.debug("\nBroker: " + marketPosition.getBroker() + " Overall Balance: " + marketPosition.getOverallBalance()
				+ "\n Timeslot:\n serialnumber: " + marketPosition.getTimeslot().getSerialNumber()
				+ " Timeslot\n Serial Number: " + marketPosition.getTimeslot().getSerialNumber() + " Index: "
				+ helper.computeRelativeTimeslotIndex(marketPosition.getTimeslot().getStartInstant()));
		// TODO

	}

	public void handleMessage(SimResume simResume) {
		// TODO

	}
	
	public void handleMessage(DistributionReport report) {
		log.debug("DIST REPORT: " + "PROD " + report.getTotalProduction() + "CONS " + report.getTotalConsumption());
	}

	public void initialize() {
		for (Class<?> clazz : Arrays.asList(DistributionReport.class,
				 SimResume.class, MarketPosition.class,
				MarketTransaction.class, BankTransaction.class,
				WeatherForecast.class, WeatherReport.class, SimPause.class, SimStart.class, TimeslotComplete.class,
				TimeslotUpdate.class, Competition.class)) {
			router.registerMessageHandler(this, clazz);
		}
	}

}