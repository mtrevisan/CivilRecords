package unit731.civilrecords.familysearch;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import unit731.civilrecords.services.HttpUtils;


@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
public class FSRequest extends HttpUtils.RequestBody{

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


	private FSRequest(){}

	private FSRequest(Type type){
		this.type = type;
		args.put(KEY_STATE, Collections.emptyMap());
	}

	public static final FSRequest createImageRequest(String imageURL){
		FSRequest request = new FSRequest(Type.IMAGE_DATA);
		request.args.put(KEY_IMAGE_URL, imageURL);
		return request;
	}

	public static final FSRequest createFilmRequest(String filmNumber){
		FSRequest request = new FSRequest(Type.FILM_DATA);
		request.args.put(KEY_FILM_NUMBER, filmNumber);
		return request;
	}

	public static final FSRequest createWaypointRequest(String waypointURL){
		FSRequest request = new FSRequest(Type.WAYPOINT_DATA);
		request.args.put(KEY_WAYPOINT_URL, waypointURL);
		return request;
	}

}
