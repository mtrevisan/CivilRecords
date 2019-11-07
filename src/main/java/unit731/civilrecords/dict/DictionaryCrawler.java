package unit731.civilrecords.dict;

import com.itextpdf.text.Document;
import com.itextpdf.text.pdf.PdfWriter;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import unit731.civilrecords.services.AbstractCrawler;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


//http://collezioni.comune.belluno.it/greenstone/cgi-bin/library.cgi?e=d-01000-00---off-0bibstori--00-1----0-10-0---0---0direct-10--TI--4-------0-1l--11-en-50---20-about-vocabolario--00-3-1-00-00--4--0--0-0-11-10-0utfZz-8-00&a=q
public class DictionaryCrawler extends AbstractCrawler{

	//[ms]
	private static final int ERROR_WAIT_TIME_DEFAULT = 10_000;


	public DictionaryCrawler(){
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
		return currentPageIndex;
	}

	@Override
	@SuppressWarnings("SleepWhileInLoop")
	protected String extractPage(String imageURL, Document document, PdfWriter writer) throws IOException{
		String nextURL = null;
		while(!shutdown){
			try{
				extractImage(imageURL, document, writer);

				String path = FilenameUtils.getFullPath(imageURL);
				String filename = FilenameUtils.getBaseName(imageURL);
				String extension = FilenameUtils.getExtension(imageURL);
				Pattern pattern = Pattern.compile("_(\\d+)$");
				Matcher matcher = pattern.matcher(filename);
				matcher.find();
				String indexAsString = matcher.group(1);
				currentPageIndex = Integer.parseInt(indexAsString);
				if(currentPageIndex >= totalPages)
					break;

				String nextIndex = leftPadWithZeros(Integer.toString(currentPageIndex + 1), indexAsString.length());
				nextURL = path + filename.substring(0, filename.length() - indexAsString.length()) + nextIndex + "." + extension;

				break;
			}
			catch(IOException e){
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

	private String leftPadWithZeros(String inputString, int length){
		if(inputString.length() >= length){
			return inputString;
		}
		StringBuilder sb = new StringBuilder();
		while(sb.length() < length - inputString.length())
			sb.append('0');
		sb.append(inputString);

		return sb.toString();
	}

	@Override
	protected void logPageStats(DescriptiveStatistics stats){
		int currPage = currentPageIndex + 1;
		int percPages = (int)Math.floor((currPage) * 100. / totalPages);
		if(percPages < 100){
			//[s/page]
			double speed = stats.getMean();
			//[min]
			int estimatedTimeToComplete = (int)Math.ceil((totalPages - currentPageIndex) * speed / 60.);
			System.out.format(Locale.ENGLISH, "Page %s/%s (%2d%%) downloaded and added to PDF (%3.1f s/page, ETA %02d:%02d)                   \r",
				currPage, (totalPages > 0? totalPages: "?"), percPages, speed, estimatedTimeToComplete / 60, estimatedTimeToComplete % 60);
		}
		else
			System.out.format(Locale.ENGLISH, "%s pages downloaded and added to PDF                                                           \r",
				currPage, (totalPages > 0? totalPages: "?"), percPages);
	}

}
