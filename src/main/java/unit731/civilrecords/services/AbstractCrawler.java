package unit731.civilrecords.services;

import com.itextpdf.text.BadElementException;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Image;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.http.client.HttpResponseException;


public abstract class AbstractCrawler{

	private static final Logger LOGGER = Logger.getLogger(AbstractCrawler.class.getName());

	//[ms]
	public static final int INTERRUPT_WAIT_TIME = 2 * 60 * 1000;

	//[ms]
	private static final int DEFAULT_REQUEST_SLEEP = 9_000;
	//[ms]
	private static final int MAX_REQUEST_SLEEP = 3 * 60_000;

	public static final double TOO_MANY_REQUEST_FACTOR = 2.;

	private static final String CONFIG_FILE = "config.properties";
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");


	private int requestWaitTime;
	private final int errorWaitTime;

	private int currentRequestTry;

	private Thread thread;
	private String username;
	private String password;
	protected volatile boolean shutdown;
	protected volatile boolean shutdownBeforeCurrentPage;

	private String startingURL;
	private String nextURLToDownload;

	private final Map<String, Integer> exceptions = new HashMap<>();


	protected AbstractCrawler(int errorWaitTime){
		this.errorWaitTime = errorWaitTime;
	}

	public void startThread(String archiveURL, String username, String password, String outputFilePath){
		if(thread != null)
			stopThread();

		this.username = username;
		this.password = password;
		shutdown = false;
		exceptions.clear();

		thread = new Thread(){
			@Override
			public void run(){
				boolean loggedIn = false;
				try{
					loggedIn = login(username, password);
				}
				catch(IOException e){
					LOGGER.log(Level.SEVERE, null, e);

					addException(e);
				}

				if(loggedIn)
					readDocument(archiveURL, outputFilePath);
			}
		};

		thread.start();
	}

	public void stopThread(){
		shutdown = true;

		thread.interrupt();
		try{
			thread.join(INTERRUPT_WAIT_TIME);
		}
		catch(InterruptedException e){
			System.out.println();
		}

		writeNextURLToDownload(nextURLToDownload);
	}

	private void readDocument(String archiveURL, String outputFilePath){
		DescriptiveStatistics stats = new DescriptiveStatistics(4);

		long start = System.currentTimeMillis();

		Document document = new Document();
		try{
			outputFilePath = findNextAvailableFilename(outputFilePath);

			PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFilePath));
			document.open();

			System.out.format("Created PDF: %s" + LINE_SEPARATOR, outputFilePath);

			startingURL = archiveURL;
			nextURLToDownload = readNextURLToDownload(archiveURL);
			while(nextURLToDownload != null && !shutdown){
				long cycleStart = System.currentTimeMillis();

				nextURLToDownload = extractPage(nextURLToDownload, document, writer);

				//[s]
				double cycleDuration = (System.currentTimeMillis() - cycleStart) / 1000.;
				stats.addValue(cycleDuration);

				logPageStats(stats);
			}
		}
		catch(IOException | DocumentException e){
			LOGGER.log(Level.SEVERE, null, e);

			addException(e);
		}
		finally{
			document.close();
		}

		//[s]
		double delta = (System.currentTimeMillis() - start) / 1000.;
		System.out.format(Locale.ENGLISH, LINE_SEPARATOR + "Done in %.1f mins", delta / 60.);
		System.out.format(Locale.ENGLISH, LINE_SEPARATOR + "Request wait time %d ms", requestWaitTime);
		if(!exceptions.isEmpty()){
			System.out.print(LINE_SEPARATOR + "Exceptions:");
			exceptions.entrySet().stream()
				.forEach(e -> System.out.format(LINE_SEPARATOR + "\t%s (%d)", e.getKey(), e.getValue()));
		}
	}

	private String readNextURLToDownload(String startingURL) throws IOException{
		try(InputStream input = new FileInputStream(CONFIG_FILE)){
			Properties prop = new Properties();
			prop.load(input);

			String nextURL = prop.getProperty(startingURL);
			if(nextURL != null)
				startingURL = nextURL;
		}
		catch(FileNotFoundException e){}

		System.out.format("Next URL to download is %s" + LINE_SEPARATOR, startingURL);
		return startingURL;
	}

	private void writeNextURLToDownload(String url){
		if(url != null)
			System.out.format("Store next URL to download: %s" + LINE_SEPARATOR, url);

		Properties prop = new Properties();

		//load properties file if it exists
		if((new File(CONFIG_FILE)).exists()){
			try(FileInputStream input = new FileInputStream(CONFIG_FILE)){
				prop.load(input);
			}
			catch(IOException e){
				LOGGER.log(Level.SEVERE, null, e);
			}
		}

		//write startingURL and nextURLToDownload
		try(OutputStream output = new FileOutputStream(CONFIG_FILE)){
			if(url != null)
				prop.setProperty(startingURL, url);
			else if(startingURL != null)
				prop.remove(startingURL);
			prop.store(output, null);
		}
		catch(IOException e){
			LOGGER.log(Level.SEVERE, null, e);
		}
	}

//	private void removeFile(String filename){
//		try{
//			Files.deleteIfExists(Paths.get(filename));
//		}
//		catch(IOException e){
//			LOGGER.log(Level.SEVERE, null, e);
//		}
//	}

	/**
	 * Returns the next available non-existent file by incrementing a numerical value at the end of the file name.
	 *
	 * @param originalFile	Filename to check the existence
	 * @return	The new next available filename
	 */
	private String findNextAvailableFilename(String originalFile){
		Path originalPath = Paths.get(originalFile);
		String extension = "." + FilenameUtils.getExtension(originalPath.toString());

		//loop until the file does not exist
		Path path = originalPath;
		while(Files.exists(path)){
			//find and extract the number currently used
			String newFilename = path.getFileName().toString();
			String matcher = "(.+\\-)(\\d{2})" + Pattern.quote(extension);
			if(newFilename.matches(matcher)){
				int number = Integer.valueOf(newFilename.replaceFirst(matcher, "$2"));
				newFilename = newFilename.replaceFirst(matcher, "$1" + String.format("%02d", number + 1) + extension);
			}
			else{
				//there is no number currently used so insert a number
				String filenameWithoutExtension = newFilename.substring(0, newFilename.length() - extension.length());
				newFilename = filenameWithoutExtension + "-01" + extension;
			}

			path = originalPath.getParent().resolve(newFilename);
		}

		return path.toString();
	}

	protected boolean login(String username, String password) throws IOException{
		return true;
	}

	protected abstract String extractPage(String url, Document document, PdfWriter writer) throws IOException;

	protected void extractImage(String url, Document document, PdfWriter writer){
		while(!shutdown){
			try{
				byte[] raw = getRawImage(url);

				addImageToDocument(raw, document, writer);

				currentRequestTry = 0;

				try{ Thread.sleep(DEFAULT_REQUEST_SLEEP); }
				catch(InterruptedException ie){}

				break;
			}
			catch(HttpResponseException e){
				addException(e);

				adjustRequestWaitTime();

				try{
					login(username, password);
				}
				catch(IOException e2){
					LOGGER.log(Level.SEVERE, null, e);
				}
			}
			catch(DocumentException | IOException | URISyntaxException e){
				addException(e);

//				System.out.format("\n");
//				LOGGER.log(Level.SEVERE, null, e);

				if(shutdown){
					shutdownBeforeCurrentPage = true;
				}
				else{
					try{ Thread.sleep(errorWaitTime); }
					catch(InterruptedException ie){}
				}
			}
		}
	}

	private void adjustRequestWaitTime(){
		if(currentRequestTry > 0){
			requestWaitTime = getSleepDuration(currentRequestTry, DEFAULT_REQUEST_SLEEP, MAX_REQUEST_SLEEP);

			System.out.format(Locale.ENGLISH, LINE_SEPARATOR + "Adjust request wait time to %d ms", requestWaitTime);

			try{ Thread.sleep(requestWaitTime); }
			catch(InterruptedException ie){}
		}
	}

	private int getSleepDuration(int currentTry, int minSleep, int maxSleep){
		int currentSleep = (int)(minSleep * Math.pow(TOO_MANY_REQUEST_FACTOR, currentTry));
		return Math.min(currentSleep, maxSleep);
	}

	protected String extractNextURL(String url){
		while(!shutdown || !shutdownBeforeCurrentPage){
			try{
				url = getNextURL(url);

				currentRequestTry = 0;

				break;
			}
			catch(IOException | URISyntaxException e){
				addException(e);

//				System.out.format("\n");
//				LOGGER.log(Level.SEVERE, null, e);

				try{ Thread.sleep(errorWaitTime); }
				catch(InterruptedException ie){}
			}
		}
		return url;
	}

	private void addException(Exception e){
		String text = e.getMessage();
		if("Too Many Requests".equals(text))
			currentRequestTry ++;

		Integer count = exceptions.get(text);
		if(count == null)
			count = 0;
		exceptions.put(text, count + 1);
	}

	private byte[] getRawImage(String url) throws URISyntaxException, IOException{
		return HttpUtils.getRequestAsContent(url)
			.asBytes();
	}

	private void addImageToDocument(byte[] raw, Document document, PdfWriter writer) throws BadElementException, IOException, DocumentException{
		Image img = Image.getInstance(raw);
		img.setAbsolutePosition(0, 0);
		document.setPageSize(img);
		document.newPage();

		PdfContentByte canvas = writer.getDirectContentUnder();
		canvas.addImage(img);
	}

	protected String getNextURL(String url) throws URISyntaxException, IOException{
		return null;
	}

	protected abstract void logPageStats(DescriptiveStatistics stats);

}
