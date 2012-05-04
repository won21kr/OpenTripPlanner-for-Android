package org.opentripplanner.android.util;

import java.text.DateFormat;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.opentripplanner.android.R;
import org.opentripplanner.android.model.Direction;
import org.opentripplanner.api.model.AbsoluteDirection;
import org.opentripplanner.api.model.AgencyAndId;
import org.opentripplanner.api.model.Leg;
import org.opentripplanner.api.model.Place;
import org.opentripplanner.api.model.RelativeDirection;
import org.opentripplanner.api.model.WalkStep;
import org.opentripplanner.routing.core.TraverseMode;
import org.opentripplanner.routing.patch.Alerts;

public class ItineraryDecrypt {
	private List<Leg> legs = new ArrayList<Leg>();

	private ArrayList<Direction> directions = new ArrayList<Direction>();

	private double totalDistance = 0;

	private double totalTimeTraveled = 0;

	public ItineraryDecrypt(List<Leg> legs){
		this.legs.addAll(legs);

		convertToDirectionList();
	}

	/**
	 * @return the directions
	 */
	public ArrayList<Direction> getDirections() {
		return directions;
	}

	/**
	 * @param directions the directions to set
	 */
	public void setDirections(ArrayList<Direction> directions) {
		this.directions = directions;
	}

	public void addDirection(Direction dir){
		if(directions==null){
			directions = new ArrayList<Direction>();
		}

		directions.add(dir);
	}

	private void convertToDirectionList(){
		for(Leg leg: legs){
			setTotalDistance(getTotalDistance() + leg.distance);

			TraverseMode traverseMode = TraverseMode.valueOf((String) leg.mode);
			if(traverseMode.isOnStreetNonTransit()){
				Direction dir = decryptNonTransit(leg);
				if(dir == null){
					continue;
				}
				addDirection(dir);
			} else{
				ArrayList<Direction> directions = decryptTransit(leg);
				if(directions == null){
					continue;
				}

				if(directions.get(0)!=null){
					addDirection(directions.get(0));
				}

				if(directions.get(1)!=null){
					addDirection(directions.get(1));
				}
			}

			//			directionText+=leg.mode+"\n";
		}
	}

	private Direction decryptNonTransit(Leg leg){
		Direction direction = new Direction();

		//		http://opentripplanner.usf.edu/opentripplanner-api-webapp/ws/plan?optimize=QUICK&time=09:24pm&arriveBy=false&wheelchair=false&maxWalkDistance=7600.0&fromPlace=28.033389%2C+-82.521034&toPlace=28.064709%2C+-82.471618&date=03/07/12&mode=WALK,TRAM,SUBWAY,RAIL,BUS,FERRY,CABLE_CAR,GONDOLA,FUNICULAR,TRANSIT,TRAINISH,BUSISH

		// Get appropriate action and icon
		String action = "Walk";
		int icon = R.drawable.mode_walk;
		TraverseMode mode = TraverseMode.valueOf((String) leg.mode);
		if(mode.compareTo(TraverseMode.BICYCLE)==0){
			action = "Bike";
			icon = R.drawable.mode_bicycle;
		} else if(mode.compareTo(TraverseMode.CAR)==0){
			action = "Drive";
			icon = R.drawable.icon;
		}

		direction.setIcon(icon);

		//		Main direction
		Place fromPlace = leg.from;
		Place toPlace = leg.to;
		String mainDirectionText = action;
		mainDirectionText += fromPlace.name==null ? "" : " from " + fromPlace.name;
		mainDirectionText += toPlace.name==null ? "" : " to " + toPlace.name;
		mainDirectionText += toPlace.stopId==null ? "" : " (" + toPlace.stopId.getAgencyId() + " " + toPlace.stopId.getId() + ")";
//		double duration = DateTimeConversion.getDuration(leg.startTime, leg.endTime);
		double totalDistance = leg.distance;
		DecimalFormat twoDForm = new DecimalFormat("#.##");
		totalDistance = Double.valueOf(twoDForm.format(totalDistance));
		mainDirectionText += "\n[" + totalDistance + "meters ]";// Double.toString(duration);

		direction.setDirectionText(mainDirectionText);

		//		Sub-direction
		List<WalkStep> walkSteps = leg.walkSteps;

		if(walkSteps==null) return direction;

		ArrayList<Direction> subDirections = new ArrayList<Direction>(walkSteps.size());

		for(WalkStep step: walkSteps){
			Direction dir = new Direction();
			String subDirectionText = "";

			double distance = step.distance;
			// Distance traveled [distance]
			distance = Double.valueOf(twoDForm.format(distance));
			dir.setDistanceTraveled(distance);

			RelativeDirection relativeDir = step.relativeDirection;
			String streetName = step.streetName;
			AbsoluteDirection absoluteDir = step.absoluteDirection;
			String exit = step.exit;
			boolean isStayOn = (step.stayOn==null ? false : step.stayOn);
			boolean isBogusName = (step.bogusName==null ? false : step.bogusName);
			double lon = step.lon;
			double lat = step.lat;
			String elevation = step.elevation;
			List<Alerts> alert = step.alerts;

			// Walk East
			if(relativeDir==null){
				subDirectionText += action + " ";
				subDirectionText += absoluteDir.name() + " ";
			}
			// (Turn left)/(Continue) 
			else {
				if(!isStayOn) {
					RelativeDirection rDir = RelativeDirection.valueOf(relativeDir.name());

					// Do not need TURN Continue
					if( rDir.compareTo(RelativeDirection.CONTINUE) != 0 &&
							rDir.compareTo(RelativeDirection.CIRCLE_CLOCKWISE) !=0 &&
							rDir.compareTo(RelativeDirection.CIRCLE_COUNTERCLOCKWISE) != 0){
						subDirectionText += "Turn ";
					}

					subDirectionText += relativeDir.name() + " ";
				} else {
					subDirectionText += relativeDir.name() + " ";
				}
			}

			// (on ABC)
			//			if(!isBogusName) {
			//				subDirectionText += "on "+ streetName + " ";
			//			}

			subDirectionText += "on "+ streetName + " ";

			subDirectionText += "\n[" + Double.toString(distance) + "meters ]";

			dir.setDirectionText(subDirectionText);

			dir.setIcon(icon);

			// Add new sub-direction
			subDirections.add(dir);
		}

		direction.setSubDirections(subDirections);

		return direction;
	}

	private ArrayList<Direction> decryptTransit(Leg leg){
		ArrayList<Direction> directions = new ArrayList<Direction>(2);
		Direction onDirection = new Direction();
		Direction offDirection = new Direction(); 

		//		set icon
		TraverseMode mode = TraverseMode.valueOf((String) leg.mode);
		int icon = R.drawable.mode_bus;
		if(mode.compareTo(TraverseMode.RAIL)==0){
			icon = R.drawable.mode_rail;
		} else if(mode.compareTo(TraverseMode.FERRY)==0){
			icon = R.drawable.mode_ferry;
		} else if(mode.compareTo(TraverseMode.GONDOLA)==0){
			icon = R.drawable.mode_gondola;
		} else if(mode.compareTo(TraverseMode.SUBWAY)==0){
			icon = R.drawable.mode_subway;
		} else if(mode.compareTo(TraverseMode.TRAM)==0){
			icon = R.drawable.mode_tram;
		}

		onDirection.setIcon(icon);

		//		set direction text
		String onDirectionText = "";
		String offDirectionText = "";

		String route = leg.route;
		String agencyName = leg.agencyName;
		String agencyUrl = leg.agencyId;
		String routeColor = leg.routeColor;
		String routeTextColor = leg.routeTextColor;
		boolean isInterlineWithPreviousLeg = (leg.interlineWithPreviousLeg==null?false:leg.interlineWithPreviousLeg);
		String tripShortName = leg.tripShortName;
		String headsign = leg.headsign;
		String agencyId = leg.agencyId;
		String routeShortName = leg.routeShortName;
		String routeLongName = leg.routeLongName;
		String boardRule = leg.boardRule;
		String alignRule = leg.alightRule;

		ArrayList<Place> stopsInBetween = new ArrayList<Place>();
		if(leg.stops!=null)
			stopsInBetween.addAll(leg.stops);

		double distance = leg.distance;
		Place from = leg.from;
		AgencyAndId agencyAndIdFrom = from.stopId;
		Place to = leg.to;
		AgencyAndId agencyAndIdTo = to.stopId;
		long duration = leg.duration;

		// Get on HART BUS 6
		String serviceName = agencyName;
		if(serviceName==null)
			serviceName = agencyId;

		offDirectionText += "Get off " + serviceName + " " + mode + " " + route + "\n";
		offDirectionText += "At " + to.name + " (" + agencyAndIdTo.getAgencyId() + " " + agencyAndIdTo.getId() + ")";
		offDirection.setDirectionText(offDirectionText);
		offDirection.setIcon(icon);

		// Only onDirection has subdirection (list of stops in between)
		onDirectionText += "Get on " + serviceName + " " + mode + " " + route + "\n";
		onDirectionText += "At " + from.name + " (" + agencyAndIdFrom.getAgencyId() + " " + agencyAndIdFrom.getId() + ")\n";
		onDirectionText += stopsInBetween.size() + " stops in between";
		onDirection.setDirectionText(onDirectionText);
		onDirection.setIcon(icon);

		// sub-direction
		ArrayList<Direction> subDirections = new ArrayList<Direction>();
		for(int i=0; i<stopsInBetween.size(); i++){
			Direction subDirection = new Direction();

			Place stop = stopsInBetween.get(i);
			AgencyAndId agencyAndIdStop = stop.stopId;
			String subDirectionText = 	Integer.toString(i) + ". " +stop.name + " (" + 
					agencyAndIdStop.getAgencyId() + " " + 
					agencyAndIdStop.getId() + ")";

			subDirection.setDirectionText(subDirectionText);
			subDirection.setIcon(icon);

			subDirections.add(subDirection);
		}
		onDirection.setSubDirections(subDirections);

		// Distance traveled [distance]
		DecimalFormat twoDForm = new DecimalFormat("#.##");
		distance = Double.valueOf(twoDForm.format(distance));
		onDirection.setDistanceTraveled(distance);

		directions.add(onDirection);
		directions.add(offDirection);

		return directions;
	}

	/**
	 * @return the totalDistance
	 */
	public double getTotalDistance() {
		DecimalFormat twoDForm = new DecimalFormat("#.##");
		totalDistance = Double.valueOf(twoDForm.format(totalDistance));
		return totalDistance;
	}

	/**
	 * @param totalDistance the totalDistance to set
	 */
	public void setTotalDistance(double totalDistance) {
		this.totalDistance = totalDistance;
	}

	/**
	 * @return the totalTimeTraveled
	 */
	public double getTotalTimeTraveled() {
		if(legs.isEmpty()) return 0;

		Leg legStart = legs.get(0);
		String startTimeText = legStart.startTime;
		Leg legEnd = legs.get(legs.size()-1);
		String endTimeText = legEnd.endTime;

		totalTimeTraveled = DateTimeConversion.getDuration(startTimeText, endTimeText);

		return totalTimeTraveled;
	}

}