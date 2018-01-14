
/*
 *
 */

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Random;

// for stats
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import com.swirlds.platform.Statistics;

// temp
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.swirlds.platform.Browser;
import com.swirlds.platform.Console;
import com.swirlds.platform.Platform;
import com.swirlds.platform.SwirldMain;
import com.swirlds.platform.SwirldState;

/**
 * This demonstrates a cryptocurrency exchange.
 * There are several currencies (depend on parameters) and each member
 * repeatedly generates an order to exchange one for another.
 * Rates are temporarily fixed.
 */
public class CryptoExchangeMain implements SwirldMain {
	private final boolean verboseOutput = false;
	private final boolean statsToFile = true;
	/** temporary counter */
	public long transactionsCreated = 0;
	public Instant timeStarted; // temp
	/** Number of transactions to generate per second */
	private int genTransSec;
	/** temp: USD amount of each order */
	private final static long fixed_amount = 1000;
	/** should this run with no windows? */
	private boolean headless = true;
	/** time to delay between screen updates, in milliseconds (250 for 4 times a second) */
	// private final long screenUpdateDelay = 250;
	private final long screenUpdateDelay = 1000;
	/** the app is run by this */
	private Platform platform;
	/** ID number for this member */
	private int selfId;
	/** a console window for text output */
	private Console console = null;
	/** used to randomly choose ask/bid and prices */
	private Random rand = new Random();
	/** so user can use arrows and spacebar */
	private GuiKeyListener keyListener = new GuiKeyListener();
	/** if not -1, then need to create a transaction to sync fast or slow */
	private byte speedCmd = -1;
	/** is the simulation running fast now? */
	private boolean isFast = false;
	/** number of different currencies that can be exchanged */
	private int numCurrencies;
	/** path and filename of the .csv file to write to */
	private String path;

	/** Listen for input from the keyboard, and remember the last key typed. */
	private class GuiKeyListener implements KeyListener {
		@Override
		public void keyReleased(KeyEvent e) {
		}

		@Override
		public void keyPressed(KeyEvent e) {
			if (e.getKeyChar() == 'F' || e.getKeyChar() == 'f') {
				isFast = true;
				speedCmd = (byte) CryptoExchangeState.TransType.fast
						.ordinal();
			} else if (e.getKeyChar() == 'S' || e.getKeyChar() == 's') {
				isFast = false;
				speedCmd = (byte) CryptoExchangeState.TransType.slow
						.ordinal();
			}
		}

		@Override
		public void keyTyped(KeyEvent e) {
		}
	}

	/**
	 * This is just for debugging: it allows the app to run in Eclipse. If the config.txt exists and lists a
	 * particular SwirldMain class as the one to run, then it can run in Eclipse (with the green triangle
	 * icon).
	 *
	 * @param args
	 *            these are not used
	 */
	public static void main(String[] args) {
		Browser.main(args);
	}

	/**
	 * Write a message to the log file. Also write it to the console, if there is one. In both cases, skip a
	 * line after writing, if newline is true. This method opens the file at the start and closes it at the
	 * end, to deconflict with any other process trying to read the same file. For example, this app could
	 * run headless on a server, and an FTP session could download the log file, and the file it received
	 * would have only complete log messages, never half a message.
	 * <p>
	 * The file is created if it doesn't exist. It will be named "StatsDemo0.csv", with the number
	 * incrementing for each member currently running on the local machine, if there is more than one. The
	 * location is the "current" directory. If run from a shell script, it will be the current folder that
	 * the shell script has. If run from Eclipse, it will be at the top of the project folder. If there is a
	 * console, it prints the location there. If not, it can be found by searching the file system for
	 * "StatsDemo0.csv".
	 *
	 * @param message
	 *            the String to write
	 * @param newline
	 *            true if a new line should be started after this one
	 */
	private void write(String message, boolean newline) {
		BufferedWriter file = null;
		try {// create or append to file in current directory
			path = System.getProperty("user.dir") + File.separator + "StatsDemo"
					+ selfId + ".csv";
			file = new BufferedWriter(new FileWriter(path, true));
			if (newline) {
				file.write("\n");
			} else {
				file.write(message.trim().replaceAll(",", "") + ",");
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (file != null) {
				try {
					file.close();
				} catch (Exception e) {
				}
			}
		}
		if (console != null) {
			console.out.print(newline ? "\n" : message);
		}
	}

	/** Erase the existing file (if one exists) */
	private void eraseFile() {
		BufferedWriter file = null;
		try {// erase file in current directory
			path = System.getProperty("user.dir") + File.separator + "StatsDemo"
					+ selfId + ".csv";
			file = new BufferedWriter(new FileWriter(path, false));
			file.write("");
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (file != null) {
				try {
					file.close();
				} catch (Exception e) {
				}
			}
		}
	}

	/**
	 * Same as writeToConsolAndFile, except it does not start a new line after it.
	 *
	 * @param message
	 *            the String to write
	 */
	private void write(String message) {
		write(message, false);
	}

	/** Start the next line, for both console and file. */
	private void newline() {
		write("", true);
	}

	// ///////////////////////////////////////////////////////////////////

	@Override
	public void preEvent() {
		// CryptoExchangeState state = (CryptoExchangeState) platform
				// .getState();
	}

	@Override
	public void init(Platform platform, int id) {
		this.platform = platform;
		String[] pars = platform.getParameters(); // read parameters from config.txt
		this.numCurrencies = pars.length -1;
		this.genTransSec = Integer.parseInt(pars[0]);
		this.selfId = id;
		if (!headless) { // create the window, make it visible
			this.console = platform.createConsole(true); // create the window, make it visible
			this.console.addKeyListener(keyListener);
		}
		platform.setAbout("Cryptocurrency market demo v. 1.0\n"); // set the browser's "about" box
		timeStarted = Instant.now(); //temp
	}

	@Override
	public void run() {
		Statistics statsObj = platform.getStats();
		String[][] stats = statsObj.getAvailableStats();
		// the first node saves stats if needed
		if (statsToFile & this.selfId==0) {
			// erase the old file, if any
			eraseFile();
			// write the column headings
			for (int i = 0; i < stats.length; i++) {
				write(String.format("%" + statsObj.getStatString(i).length() + "s",
						stats[i][0]));
			}
			newline();
		}

		// long seq = 1;
		// loop forever
		while (true) {
			CryptoExchangeState state = (CryptoExchangeState) platform
					.getState();
			if (console != null) {
				console.setHeading(" Cryptocurrency and Stock Market Demo\n"
						+ " Press F for fast sync, S for slow, (currently "
						+ (isFast ? "fast" : "slow") + ")\n"
						+ String.format(" %d",
								(int) platform.getStats().getStat("trans/sec"))
						+ " transactions per second for member " + selfId + "\n\n"
						+ " count  ticker  price change  change%  seller->buyer");
			}
		// create <genTransSec> transactions
		for (int i=0; i<genTransSec; i++) {
			// create one order/cancel for a random pair of currencies
			byte sell = (byte) rand.nextInt(this.numCurrencies); // randomly choose a currency
			byte buy  = (byte) rand.nextInt(this.numCurrencies); // randomly choose a currency
			if (sell == buy) {
				buy = (byte) ((buy + 1) % this.numCurrencies);
			}
			byte transType = (byte) CryptoExchangeState.TransType.order.ordinal();
			// 25% of transactions - cancel
			if (0 == rand.nextInt(4)) {
				transType = (byte) CryptoExchangeState.TransType.cancel.ordinal();
			}

			long amount = Math.round(fixed_amount * ((double)CryptoExchangeState.divisor / state.tickerRate[(int) sell])); // temp
			double rate = state.tickerRate[buy] / state.tickerRate[sell];
			// create an object of class Order (order or cancel)
			CryptoExchangeOrder newOrder = new CryptoExchangeOrder(transType, sell, buy, amount, rate);
			platform.createTransaction(newOrder.byteBuffer(), null);

			// temp
			transactionsCreated++;
			if (verboseOutput & this.selfId==0) {
				long millis = timeStarted.until(Instant.now(), ChronoUnit.MILLIS);

				System.out.println(String.format("trans - Created: %d; rate: %f; ratio: %f; proc.rate: %f tr/sec; transactionsConsensus rate: %f; Trades rate: %f", transactionsCreated, 1000.*transactionsCreated/millis, 1.*state.transactionsProcessed/transactionsCreated, 1000.*state.transactionsProcessed/millis, 1.*state.transactionsConsensusProcessed/state.transactionsProcessed, 1000.*state.getNumTrades()/millis));
			}
			// end of temp
		}	//		for (int i=0; i<genTransSec; i++) {
			if (speedCmd != -1) {
				platform.createTransaction(new byte[] { speedCmd }, null);
				speedCmd = -1;
			}
/*
			long lastSeq = state.getNumTrades();
			for (; seq < lastSeq; seq++) {
				String s = state.getTrade(seq);
				if (!s.equals("")) {
					if (console != null) {
						console.out.println(s);
					} else {
						if (this.selfId==0) {
							// System.out.println(s); // temp
						}
					}
				}
			}
*/

			// the first node saves stats if needed
			if (statsToFile & this.selfId==0) {
				// try {
					// write a row of numbers
					for (int i = 0; i < stats.length; i++) {
						write(statsObj.getStatString(i));
					}
					newline();
				// } catch (InterruptedException e) {
				// }
			}

			try {
				Thread.sleep(screenUpdateDelay);
			} catch (Exception e) {
			}
		}
	}

	@Override
	public SwirldState newState() {
		return new CryptoExchangeState();
	}
}
