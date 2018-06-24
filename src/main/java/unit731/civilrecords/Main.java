package unit731.civilrecords;

import java.util.EnumMap;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import unit731.civilrecords.familysearch.FSCrawler;
import unit731.civilrecords.san.SANCrawler;
import unit731.civilrecords.services.AbstractCrawler;


public class Main{

	private static enum Site{FS, SAN};

	static{
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
	}

	private static final EnumMap<Site, AbstractCrawler> CRAWLERS = new EnumMap<>(Site.class);
	static{
		CRAWLERS.put(Site.FS, new FSCrawler());
		CRAWLERS.put(Site.SAN, new SANCrawler());
	}


	public static void main(String[] args){
		Options options = new Options();
		defineOptions(options);

		CommandLineParser parser = new DefaultParser();
		try{
			CommandLine cmd = parser.parse(options, args);

			Site site = Site.valueOf(cmd.getOptionValue("site"));
			String archiveURL = cmd.getOptionValue("archive");
			String username = cmd.getOptionValue("username");
			String password = cmd.getOptionValue("password");

			if(site == Site.FS){
				if(username == null || username.trim().length() == 0 || password == null || password.trim().length() == 0)
					throw new ParseException("If -site is FS then the username (option -username) and password (option -password) should be provided");
				if(archiveURL == null)
					throw new ParseException("If -site is FS then either archive URL (option -archive) or catalog number (option -catalog) should be provided");
			}

			String outputFilePath = cmd.getOptionValue("output");
			AbstractCrawler crawler = CRAWLERS.get(site);
			crawler.startThread(archiveURL, username, password, outputFilePath);

			Runtime.getRuntime().addShutdownHook(new Thread(){
				@Override
				public void run(){
					System.out.println(AbstractCrawler.LINE_SEPARATOR + "Crawler shutting down...");

					crawler.stopThread();

					System.out.println("Crawler shutdown complete" + AbstractCrawler.LINE_SEPARATOR);
				}
			});
		}
		catch(ParseException e){
			System.out.println(e.getMessage());

			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("FamilySearch", options);

			System.exit(1);
		}
	}

	private static void defineOptions(Options options) throws IllegalArgumentException{
		Option opt = new Option("s", "site", true, "site type (ex. FS or SAN)");
		opt.setRequired(true);
		options.addOption(opt);

		opt = new Option("a", "archive", true, "archive URL (ex. 3QSQ-G9WY-C7JD or Archivio+di+Stato+di+Treviso/Stato+civile+napoleonico/Sarmede/Matrimoni/1806/317/005058208_00001.jpg)");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("u", "username", true, "username");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("p", "password", true, "password");
		opt.setRequired(false);
		options.addOption(opt);

		opt = new Option("o", "output", true, "output file (ex. C:\\Users\\mauro\\Downloads\\archive.pdf)");
		opt.setRequired(true);
		options.addOption(opt);
	}

}
