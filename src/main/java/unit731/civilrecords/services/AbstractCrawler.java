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

	private static enum DownloadType{
		WAIT_EACH(9_000),
		GO_STRAIGHT(0);

		//[ms]
		private final int timeToWait;

		DownloadType(int timeToWait){
			this.timeToWait = timeToWait;
		}

		public double calculateTotalDownloadTime(int pages, double timeToDownloadSinglePage){
			//[s]
			int waitTimeBetweenDownloads = 9;
			int rateLimiterMaxDonwloads = 41;
			//[s]
			int rateLimiterWaitTime = 9 * 60;
			return (this == GO_STRAIGHT?
				//floor(pages / 41) * 9[s] * 60 + (pages - floor(pages / 41)) * time_to_download[s]
				Math.floor(pages / rateLimiterMaxDonwloads) * rateLimiterWaitTime + (pages - Math.floor(pages / rateLimiterMaxDonwloads)) * timeToDownloadSinglePage
				//pages * (9 + time_to_download[s])
				: pages * (waitTimeBetweenDownloads + timeToDownloadSinglePage));
		}
	};

	//[ms]
	public static final int INTERRUPT_WAIT_TIME = 2 * 60_000;

	private DownloadType downloadType = DownloadType.WAIT_EACH;
	//[ms]
	private static final int REQUEST_RETRY_SLEEP = 30_000;

	private static final String CONFIG_FILE = "config.properties";
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");


	private final int errorWaitTime;

	private boolean currentRequestRetry;
	private boolean firstRetry;

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
			System.out.println(LINE_SEPARATOR);
		}

		writeNextURLToDownload(nextURLToDownload);
	}

	public abstract int getCurrentPageIndex();

	public abstract int getTotalPages();

	@SuppressWarnings("SleepWhileInLoop")
	private void readDocument(String archiveURL, String outputFilePath){
		DescriptiveStatistics stats = new DescriptiveStatistics(4);
		DescriptiveStatistics pageStats = new DescriptiveStatistics(4);

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

				pageStats.addValue((System.currentTimeMillis() - cycleStart) / 1000.);

				int remainingPages = getTotalPages() - getCurrentPageIndex();
				double downloadSpeed = pageStats.getMean();
				double goStraightRemainingTime = DownloadType.GO_STRAIGHT.calculateTotalDownloadTime(remainingPages, downloadSpeed);
				double waitEachRemainingTime = DownloadType.WAIT_EACH.calculateTotalDownloadTime(remainingPages, downloadSpeed);
//				DownloadType previousDownloadType = downloadType;
				downloadType = (goStraightRemainingTime < waitEachRemainingTime? DownloadType.GO_STRAIGHT: DownloadType.WAIT_EACH);
				currentRequestRetry = false;
//				if(previousDownloadType != downloadType)
//					System.out.format(LINE_SEPARATOR + "Change download type to %s, remaining time is %.1f s" + LINE_SEPARATOR, downloadType, downloadType.calculateTotalDownloadTime(remainingPages, downloadSpeed));

				if(downloadType.timeToWait > 0){
					try{ Thread.sleep(downloadType.timeToWait); }
					catch(InterruptedException ie){}
				}

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
		if(!exceptions.isEmpty()){
			System.out.print(LINE_SEPARATOR + "Exceptions:");
			exceptions.entrySet().stream()
				.forEach(e -> System.out.format(LINE_SEPARATOR + "\t%s (%d)", e.getKey(), e.getValue()));
			System.out.print(LINE_SEPARATOR);
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

	@SuppressWarnings("SleepWhileInLoop")
	protected void extractImage(String url, Document document, PdfWriter writer){
		while(!shutdown){
			try{
				byte[] raw = getRawImage(url);

				addImageToDocument(raw, document, writer);

				if(downloadType == DownloadType.GO_STRAIGHT){
					if(currentRequestRetry)
						System.out.print(LINE_SEPARATOR);
					currentRequestRetry = false;
				}

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

//				System.out.print("\n");
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
		if(downloadType == DownloadType.GO_STRAIGHT && currentRequestRetry){
			if(firstRetry)
				firstRetry = false;

			try{ Thread.sleep(REQUEST_RETRY_SLEEP); }
			catch(InterruptedException ie){}
		}
	}

	@SuppressWarnings("SleepWhileInLoop")
	protected String extractNextURL(String url){
		while(!shutdown || !shutdownBeforeCurrentPage){
			try{
				url = getNextURL(url);

				currentRequestRetry = false;

				break;
			}
			catch(IOException | URISyntaxException e){
				addException(e);

//				System.out.print("\n");
//				LOGGER.log(Level.SEVERE, null, e);

				try{ Thread.sleep(errorWaitTime); }
				catch(InterruptedException ie){}
			}
		}
		return url;
	}

	private void addException(Exception e){
		String text = e.getMessage();
		if(!currentRequestRetry && "Too Many Requests".equals(text)){
			currentRequestRetry = true;
			firstRetry = true;
		}

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
