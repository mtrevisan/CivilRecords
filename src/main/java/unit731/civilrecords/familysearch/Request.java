package unit731.civilrecords.familysearch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.HashMap;
import java.util.Map;


@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class Request{

	private static final String KEY_IMAGE_URL = "imageURL";
	private static final String KEY_FILM_NUMBER = "dgsNum";
	private static final String KEY_WAYPOINT_URL = "waypointURL";
	private static final String KEY_STATE = "state";


	public static enum Type{
		IMAGE_DATA("image-data"),
		FILM_DATA("film-data"),
		WAYPOINT_DATA("waypoint-data");


		private final String type;


		Type(String type){
			this.type = type;
		}

		@JsonValue
		@Override
		public String toString(){
			return type;
		}
	};


	private Type type;
	private final Map<String, Object> args = new HashMap<>();


	private Request(){}

	public static final Request createImageRequest(String imageURL){
		Request request = new Request();
		request.type = Type.IMAGE_DATA;
		request.args.put(KEY_IMAGE_URL, imageURL);
		request.args.put(KEY_STATE, new HashMap<>());
		return request;
	}

	public static final Request createFilmRequest(String filmNumber){
		Request request = new Request();
		request.type = Type.FILM_DATA;
		request.args.put(KEY_FILM_NUMBER, filmNumber);
		request.args.put(KEY_STATE, new HashMap<>());
		return request;
	}

	public static final Request createWaypointRequest(String waypointURL){
		Request request = new Request();
		request.type = Type.WAYPOINT_DATA;
		request.args.put(KEY_WAYPOINT_URL, waypointURL);
		request.args.put(KEY_STATE, new HashMap<>());
		return request;
	}

}
