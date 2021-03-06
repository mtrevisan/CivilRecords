package unit731.civilrecords.san;

import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import unit731.civilrecords.services.AbstractCrawler;
import unit731.civilrecords.services.HttpUtils;


public class SANCrawler extends AbstractCrawler{

	//[ms]
	private static final int ERROR_WAIT_TIME_DEFAULT = 10_000;

	private static final String URL_SAN = "http://www.antenati.san.beniculturali.it";
	private static final String URL_SAN_ARCHIVE = URL_SAN + "/v/";


	private int pagesAdded;


	public SANCrawler(){
		super(ERROR_WAIT_TIME_DEFAULT);
	}

	@Override
	public int getCurrentPageIndex(){
		return 0;
	}

	@Override
	public int getTotalPages(){
		return 0;
	}

	@Override
	public int getPagesAdded(){
		return pagesAdded;
	}

	@Override
	@SuppressWarnings("SleepWhileInLoop")
	protected String extractPage(String url, Document document, PdfWriter writer) throws IOException{
		String nextURL = null;
		while(!shutdown){
			try{
				String content = HttpUtils.getRequestAsContent(URL_SAN_ARCHIVE + url)
					.asString(StandardCharsets.UTF_8);
				Element doc = Jsoup.parse(content);

				Elements imageLink = doc.select("a.cloud-zoom");
				String imageURL = (imageLink != null && !imageLink.isEmpty()? imageLink.get(0).absUrl("href"): null);
				pagesAdded += extractImage(imageURL, document, writer);

				Elements nextLink = doc.select("a.next");
				nextURL = (nextLink != null && !nextLink.isEmpty()? HttpUtils.cleanURLFromParameters(URL_SAN + nextLink.get(0).attr("href")): null);
				if(nextURL != null && nextURL.startsWith(URL_SAN_ARCHIVE))
					nextURL = nextURL.substring(URL_SAN_ARCHIVE.length());

				break;
			}
			catch(IOException | URISyntaxException e){
				if(!(e instanceof SocketTimeoutException))
					Logger.getLogger(AbstractCrawler.class.getName()).log(Level.SEVERE, null, e);

				if(shutdown)
					break;

				try{ Thread.sleep(ERROR_WAIT_TIME_DEFAULT); }
				catch(InterruptedException ie){}
			}
		}
		return nextURL;
	}

	@Override
	protected void logPageStats(DescriptiveStatistics stats){
		System.out.format(Locale.ENGLISH, "Page downloaded and added to PDF (%3.1f s/page)    \r", stats.getMean());
	}

}
