package unit731.civilrecords.familysearch;

import java.util.HashMap;
import java.util.Map;


public class Request{

	public static enum Type{
		IMAGE_DATA("image-data"),
		FILM_DATA("film-data"),
		WAYPOINT_DATA("waypoint-data");


		private final String type;


		Type(String type){
			this.type = type;
		}

		@Override
		public String toString(){
			return type;
		}
	};


	private Type type;
	private final Map<String, Object> args = new HashMap<>();
	private final Map<String, Object> state = new HashMap<>();


	private Request(){}

	public static final Request createImageRequest(String imageURL){
		Request request = new Request();
		request.type = Type.IMAGE_DATA;
		request.args.put("imageURL", imageURL);
		return request;
	}

}
