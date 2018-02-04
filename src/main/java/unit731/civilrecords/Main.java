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
import unit731.civilrecords.familysearch.FSFilmCrawler;
import unit731.civilrecords.san.SANCrawler;
import unit731.civilrecords.services.AbstractCrawler;


public class Main{

	private static enum Site{FS, FSFilm, SAN};

	static{
		System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.NoOpLog");
	}

	private static final EnumMap<Site, AbstractCrawler> CRAWLERS = new EnumMap<>(Site.class);
	static{
		CRAWLERS.put(Site.FS, new FSCrawler());
		CRAWLERS.put(Site.FSFilm, new FSFilmCrawler());
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
			String filmNumber = cmd.getOptionValue("film");
			if(site == Site.FSFilm && (filmNumber == null || filmNumber.isEmpty()))
				throw new ParseException("If -site is FSFilm then the film number (option -film) should be provided");
			String username = cmd.getOptionValue("username");
			String password = cmd.getOptionValue("password");
			if((site == Site.FS || site == Site.FSFilm) && (username == null || username.trim().length() == 0 || password == null)
					|| password.trim().length() == 0)
				throw new ParseException("If -site is FS or FSFilm then the username (option -username) and password (option -password) should be provided");

			String outputFilePath = cmd.getOptionValue("output");
			AbstractCrawler crawler = CRAWLERS.get(site);
			crawler.startThread(archiveURL, filmNumber, username, password, outputFilePath);

			Runtime.getRuntime().addShutdownHook(new Thread(){
				@Override
				public void run(){
					System.out.println(AbstractCrawler.LINE_SEPARATOR + "Crawler shutting down...");

					crawler.stopThread();

					System.out.println("Crawler shutdown complete");
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
		Option site = new Option("s", "site", true, "site type (ex. FS or SAN)");
		site.setRequired(true);
		options.addOption(site);

		Option archive = new Option("a", "archive", true, "archive URL (ex. 61903/3:1:3QSQ-G9WY-C7JD or Archivio+di+Stato+di+Treviso/Stato+civile+napoleonico/Sarmede/Matrimoni/1806/317/005058208_00001.jpg)");
		archive.setRequired(true);
		options.addOption(archive);

		Option film = new Option("f", "film", true, "film number (ex. 005330570)");
		film.setRequired(false);
		options.addOption(film);

		Option username = new Option("u", "username", true, "username");
		username.setRequired(false);
		options.addOption(username);

		Option password = new Option("p", "password", true, "password");
		password.setRequired(false);
		options.addOption(password);

		Option output = new Option("o", "output", true, "output file (ex. C:\\Users\\mauro\\Downloads\\archive.pdf)");
		output.setRequired(true);
		options.addOption(output);
	}

}
