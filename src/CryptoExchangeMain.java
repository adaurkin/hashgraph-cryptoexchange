
/*
 *
 */

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.Random;

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
			if (this.selfId==0) {
				long millis = timeStarted.until(Instant.now(), ChronoUnit.MILLIS);

				System.out.println(String.format("trans - Created: %d; rate: %f; ratio: %f; proc.rate: %f tr/sec; transactionsConsensus rate: %f; Trades rate: %f", transactionsCreated, 1000.*transactionsCreated/millis, 1.*state.transactionsProcessed/transactionsCreated, 1000.*state.transactionsProcessed/millis, 1.*state.transactionsConsensusProcessed/state.transactionsProcessed, 1000.*state.getNumTrades()/millis));
			}
			// end of temp
		}	//		for (int i=0; i<genTransSec; i++) {
			if (speedCmd != -1) {
				platform.createTransaction(new byte[] { speedCmd }, null);
				speedCmd = -1;
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
