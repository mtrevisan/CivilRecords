package unit731.civilrecords.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.apache.http.client.fluent.Content;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public abstract class AbstractCrawler{

	//[ms]
	public static final int WAIT_TIME = 10_000;
	public static final int INTERRUPT_WAIT_TIME = 2 * 60 * 1000;

	private static final String CONFIG_FILE = "config.properties";
	public static final String LINE_SEPARATOR = System.getProperty("line.separator");

	private final int waitTime;

	private Thread thread;
	protected volatile boolean shutdown;
	protected boolean shutdownBeforeCurrentPage;

	private String startingURL;
	private String nextURLToDownload;


	protected AbstractCrawler(int waitTime){
		this.waitTime = waitTime;
	}
	private static final Matcher FAMILYSEARCH_CATALOG_SCRIPT_CLEANER = Pattern.compile("var\\s+data\\s*=\\s*([^;]+?);").matcher("");
	private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

	public void startThread(String archiveURL, Long catalogNumber, String username, String password, String outputFilePath){
		if(thread != null)
			stopThread();

		shutdown = false;

		thread = new Thread(){
			@Override
			public void run(){
				List<String> archiveURLs = new ArrayList<>();
				if(catalogNumber != null){
					try{
						//extract list of archiveURLs
						String bla = "https://www.familysearch.org/search/catalog/" + catalogNumber;
						String catalogContent = HttpUtils.getRequestAsContent(bla)
							.asString(StandardCharsets.UTF_8);
						Element doc = Jsoup.parse(catalogContent);
						Elements elems = doc.getElementsByTag("script");
						if(elems == null || elems.isEmpty())
							throw new IOException("Invalid catalog number in URL " + bla);

						JsonNode tree = null;
						for(Element elem : elems){
							String scriptContent = elem.html();
							if(scriptContent.contains("\"" + catalogNumber + "\"")){
								FAMILYSEARCH_CATALOG_SCRIPT_CLEANER.reset(scriptContent);
								if(FAMILYSEARCH_CATALOG_SCRIPT_CLEANER.find()){
									String dataContent = FAMILYSEARCH_CATALOG_SCRIPT_CLEANER.group(1);
									tree = JSON_MAPPER.readTree(dataContent);

									break;
								}
							}
						}
						String filenamePrefix = tree.path("title").get(0).asText("place");
						ArrayNode list = (ArrayNode)tree.get("film_note");
						for(JsonNode node : list){
							String filenameSuffix = node.path("text").get(0).asText("");
							String filmNumber = node.path("filmno").get(0).asText(null);
							//TODO
							System.out.println(node);
						}
						//TODO
					}
					catch(IOException ex){
						Logger.getLogger(AbstractCrawler.class.getName()).log(Level.SEVERE, null, ex);
					}

				}
				else
					archiveURLs.add(archiveURL);

				for(String url : archiveURLs)
					readDocument(url, username, password, outputFilePath);
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
		catch(InterruptedException e){}

		if(nextURLToDownload != null)
			writeNextURLToDownload(nextURLToDownload);
		else
			writeNextURLToDownload(null);
	}

	private void readDocument(String archiveURL, String username, String password, String outputFilePath){
		DescriptiveStatistics stats = new DescriptiveStatistics(4);

		long start = System.currentTimeMillis();

		Document document = new Document();
		try{
			outputFilePath = findNextAvailableFilename(outputFilePath);

			PdfWriter writer = PdfWriter.getInstance(document, new FileOutputStream(outputFilePath));
			document.open();

			System.out.format("Created PDF: %s" + LINE_SEPARATOR, outputFilePath);

			login(username, password);

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
			Logger.getLogger(AbstractCrawler.class.getName()).log(Level.SEVERE, null, e);
		}
		finally{
			document.close();
		}

		//[s]
		double delta = (System.currentTimeMillis() - start) / 1000.;
		System.out.format(Locale.ENGLISH, LINE_SEPARATOR + "Done in %.1f mins", delta / 60.);
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
		System.out.format("Store next URL to download: %s" + LINE_SEPARATOR, url);

		Properties prop = new Properties();

		//load properties file if it exists
		if((new File(CONFIG_FILE)).exists()){
			try(FileInputStream input = new FileInputStream(CONFIG_FILE)){
				prop.load(input);
			}
			catch(IOException e){
				Logger.getLogger(AbstractCrawler.class.getName()).log(Level.SEVERE, null, e);
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
			Logger.getLogger(AbstractCrawler.class.getName()).log(Level.SEVERE, null, e);
		}
	}

//	private void removeFile(String filename){
//		try{
//			Files.deleteIfExists(Paths.get(filename));
//		}
//		catch(IOException e){
//			Logger.getLogger(AbstractCrawler.class.getName()).log(Level.SEVERE, null, e);
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

	protected void login(String username, String password) throws IOException{}

	protected abstract String extractPage(String url, Document document, PdfWriter writer) throws IOException;

	protected void extractImage(String url, Document document, PdfWriter writer){
		while(!shutdown){
			try{
				byte[] raw = getRawImage(url);

				addImageToDocument(raw, document, writer);

				break;
			}
			catch(DocumentException | IOException | URISyntaxException e){
//				System.out.format("\n");
//				Logger.getLogger(AbstractCrawler.class.getName()).log(Level.SEVERE, null, e);

				if(shutdown){
					shutdownBeforeCurrentPage = true;
				}
				else{
					try{ Thread.sleep(waitTime); }
					catch(InterruptedException ie){}
				}
			}
		}
	}

	protected String extractNextURL(String url){
		while(!shutdown || !shutdownBeforeCurrentPage){
			try{
				url = getNextURL(url);

				break;
			}
			catch(IOException | URISyntaxException e){
//				System.out.format("\n");
//				Logger.getLogger(AbstractCrawler.class.getName()).log(Level.SEVERE, null, e);

				try{ Thread.sleep(waitTime); }
				catch(InterruptedException ie){}
			}
		}
		return url;
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
