
/*
 *
 */

import java.io.IOException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Random;

import java.util.ArrayList; // for orderBook
import java.lang.reflect.Field; // temp
// import java.time.Instant;
// import java.time.temporal.Temporal;
// import java.time.temporal.ChronoUnit;
import java.time.Duration;

import com.swirlds.platform.Address;
import com.swirlds.platform.AddressBook;
import com.swirlds.platform.FCDataInputStream;
import com.swirlds.platform.FCDataOutputStream;
import com.swirlds.platform.FastCopyable;
import com.swirlds.platform.Platform;
import com.swirlds.platform.SwirldState;
import com.swirlds.platform.Utilities;

/**
 * This holds the current state of a swirld representing a cryptocurrency market.
 *
 * This is just a simulated cryptocurrency market.
 */
public class CryptoExchangeState implements SwirldState {
	/**
	 * the first byte of a transaction is the ordinal of one of these four: do not delete any of these or
	 * change the order (and add new ones only to the end)
	 */
	public static enum TransType {
		slow, fast, order, cancel // run slow/fast or broadcast an order/cancellation
	};

	/** temporary counter */
	public long transactionsProcessed = 0;
	public long transactionsConsensusProcessed = 0;
	/** divisor for amounts of currencies */
	public final static long divisor = 100*1000*1000; // 10^8
	/** in slow mode, number of milliseconds to sleep after each outgoing sync */
	private final static int delaySlowSync = 1000; // default
	// private final static int delaySlowSync = 5000;
	// private final static int delaySlowSync = 100;
	// private final static int delaySlowSync = 0; // to be deployed
	/** in fast mode, number of milliseconds to sleep after each outgoing sync */
	private final static int delayFastSync = 0;
	// private final static int delayFastSync = 1000;
	/** remember the last MAX_TRADES trades that occurred. */
	private final static int MAX_TRADES = 200;
	/** the platform running this app */
	private Platform platform = null;
	/** number of different currencies that can be exchanged */
	private int numCurrencies;
	/** id of the instance - to avoid multiple instance output */
	public int main_id;

	////////////////////////////////////////////////////
	// the following are the shared state:

	/** names and addresses of all members */
	private AddressBook addressBook;
	/** the number of members participating in this swirld */
	private int numMembers;
	/** ticker symbols for each of the stocks */
	private String[] tickerSymbol;
	/** rate in USD for each of the stocks */
	public double[] tickerRate;
	/** portfolio[m][s] is the amount of currencies that member m owns of stock s (multiplied by 10^8 for divisibility) */
	private long[][] portfolio;
	/** a record of the last NUM_TRADES trades */
	private String[] trades;
	/** number of trades currently stored in trades[] (from 0 to MAX_TRADES, inclusive) */
	private int numTradesStored = 0;
	/** the latest trade was stored in trades[lastTradeIndex] */
	private int lastTradeIndex = 0;
	/** how many trades have happened in all history */
	private long numTrades = 0;

	// private ArrayList<CryptoExchangeOrder> orderBook = new ArrayList<CryptoExchangeOrder>();
	private ArrayList<CryptoExchangeOrder> orderBooks[][];

	////////////////////////////////////////////////////

	/**
	 * get the string representing the trade with the given sequence number. The first trade in all of
	 * history is sequence 1, the next is 2, etc.
	 *
	 * @param seq
	 *            the sequence number of the trade
	 * @return the trade, or "" if it hasn't happened yet or happened so long ago that it is no longer
	 *         stored
	 */
	public synchronized String getTrade(long seq) {
		if (seq > numTrades || seq <= numTrades - numTradesStored) {
			return "";
		}
		return trades[(int) ((lastTradeIndex + seq - numTrades + MAX_TRADES)
				% MAX_TRADES)];
	}

	/**
	 * return how many trades have occurred. So getTrade(getNumTrades()) will return a non-empty string (if
	 * any trades have ever occurred), but getTrade(getNumTrades()+1) will return "" (unless one happens
	 * between the two method calls).
	 *
	 * @return number of trades
	 */
	public synchronized long getNumTrades() {
		return numTrades;
	}

	@Override
	public synchronized AddressBook getAddressBookCopy() {
		return addressBook.copy();
	}

	@Override
	public synchronized FastCopyable copy() {
		CryptoExchangeState copy = new CryptoExchangeState();
		copy.copyFrom(this);
		return copy;
	}

	@Override
	public void copyTo(FCDataOutputStream outStream) {
		System.out.println("copyTo");
	}

	@Override
	public void copyFrom(FCDataInputStream inStream) {
		System.out.println("copyFrom");
	}

	@Override
	public synchronized void copyFrom(SwirldState oldCryptoExchangeState) {
		CryptoExchangeState old = (CryptoExchangeState) oldCryptoExchangeState;

		platform = old.platform;
		addressBook = old.addressBook.copy();
		numMembers = old.numMembers;
		tickerSymbol = old.tickerSymbol.clone();
		portfolio = Utilities.deepClone(old.portfolio);
		trades = old.trades.clone();
		numTradesStored = old.numTradesStored;
		lastTradeIndex = old.lastTradeIndex;
		numTrades = old.numTrades;
		tickerRate = old.tickerRate.clone();
		main_id = old.main_id;
		orderBooks = old.orderBooks.clone();
		transactionsProcessed = old.transactionsProcessed; // temp
		transactionsConsensusProcessed = old.transactionsConsensusProcessed; // temp
	}

	/**
	 * {@inheritDoc}
	 *
	 * The matching algorithm for orders is as follows.
	 * ...
	 * <p>
	 * A transaction is 1 or more bytes:
	 * <pre>
	 * {SLOW} = run slowly
	 * {FAST} = run quickly
	 * {EXCHANGE,s,b,a,r} = order to exchange a (amount) of currency s (sell) into currency b (buy) with rate r
	 * {CANCEL,s,b,a,r} = cancel existing order to exchange a (amount) of currency s (sell) into currency b (buy) with rate r
	 * </pre>
	 */
	@Override
	public synchronized void handleTransaction(long id, boolean isConsensus,
			Instant timeCreated, byte[] transaction, Address address) {
		if (transaction == null || transaction.length == 0) {
			return;
		}

		transactionsProcessed++;

		if (transaction[0] == TransType.slow.ordinal()) {
			platform.setSleepAfterSync(delaySlowSync);
			return;
		} else if (transaction[0] == TransType.fast.ordinal()) {
			platform.setSleepAfterSync(delayFastSync);
			return;
		} else if (!isConsensus || transaction.length < 3) {
			return;// ignore any order that doesn't have consensus yet
		}

		transactionsConsensusProcessed++;

		int origId = (int) id;
		// create an object of class Order from buffer
		CryptoExchangeOrder ord = new CryptoExchangeOrder(origId, transaction);
		ord.timeCreated = timeCreated; // temp - remove or add to constructor
		// output(String.format("Consensus on a transaction aged: %f sec", ord.age()/1000.)); // temp
		byte transType= ord.type;
		byte currSell = ord.sell;
		byte currBuy  = ord.buy;
		long amount   = ord.amount;
		double rate   = ord.rate;

		// CANCEL
		// find the order in orderBook and delete it
		if (transaction[0] == TransType.cancel.ordinal()) {
			long count = orderBooks[currSell][currBuy].size(); // temp
			// delete all orders of the member with the given pair, amount and rate
			orderBooks[currSell][currBuy].removeIf(o -> o.origId == origId & o.amount == amount & o.rate == rate);
			count -= orderBooks[currSell][currBuy].size(); // temp
			// output(String.format("CANCEL order %s->%s. Orders deleted: %d", tickerSymbol[currSell], tickerSymbol[currBuy], count)); // temp
			return;
		}

		// ORDER
		// match the order or store it to an order book
		if (transType == TransType.order.ordinal()) {
			// try to match the order vs orderBook
			// "cross" currSell & currBuy here to match orders; filter out orders of the same member
			CryptoExchangeOrder[] ordersToMatch = orderBooks[currBuy][currSell].stream().filter(o -> o.origId != origId).toArray(CryptoExchangeOrder[]::new);
			// CryptoExchangeOrder[] ordersToMatch = orderBooks[currBuy][currSell].stream().filter(o -> o.origId != origId & o.rate >= 1/rate).toArray(CryptoExchangeOrder[]::new);
			// output(String.format(" ordersToMatch for %s->%s of %s: %d", tickerSymbol[currSell], tickerSymbol[currBuy], addressBook.getAddress(origId).getNickname(), ordersToMatch.length));
			if (ordersToMatch.length > 0) {
				// output(String.format("Matched a transaction aged: %f sec", ordersToMatch[0].age()/1000.)); // temp
				// perform the trade (exchanging one currency for another)
				portfolio[origId][currSell] -= amount; // latest trader gives amount of currency he sells
				portfolio[origId][currBuy]  += ordersToMatch[0].amount; // latest trader gets the corresponding amount of currency he buys

				portfolio[ordersToMatch[0].origId][currSell] += amount; // earlier trader gets amount of currency he buys (the other one sells)
				portfolio[ordersToMatch[0].origId][currBuy]  -= ordersToMatch[0].amount; // earlier trader gives the corresponding amount of currency he sells (the other one buys)
				// the trade occurs at the mean of the ask and bid

				numTrades++;
				numTradesStored = Math.min(MAX_TRADES, 1 + numTradesStored);
				lastTradeIndex = (lastTradeIndex + 1) % MAX_TRADES;

				// save a description of the trade to show on the console
				String sellerNickname = addressBook.getAddress(origId).getNickname();
				String buyerNickname = addressBook.getAddress(ordersToMatch[0].origId).getNickname();
				String tradeDescription = String.format(
						"Trade #%5d %f %3s -> %3s @ %f | %s: %s <-> %s: %s",
						numTrades, (double)amount/divisor, tickerSymbol[currSell], tickerSymbol[currBuy], (double)rate,
						sellerNickname, getPortfolio(portfolio[origId]),
						buyerNickname,  getPortfolio(portfolio[ordersToMatch[0].origId])
						);
				// record the trade
				trades[lastTradeIndex] = tradeDescription;
				// output("Added: " + tradeDescription);

				// output time from initial order creation to matching
				// output(String.format("Time to match: %s", Duration.between(ordersToMatch[0].timeCreated, timeCreated)));

				// remove matched order
				long count = orderBooks[currBuy][currSell].size();
				// key: ogigId + sell + buy ? delete all orders of the member with the given pair?
				orderBooks[currBuy][currSell].removeIf(o -> o.origId == ordersToMatch[0].origId & o.amount == ordersToMatch[0].amount & o.rate == ordersToMatch[0].rate);
				count -= orderBooks[currBuy][currSell].size();
				// output(String.format("order matched: %s->%s. Orders deleted: %d", tickerSymbol[currSell], tickerSymbol[currBuy], count));
			} else {
				// if not matched, add to orderbook
				// ToDo: count number of existing orders with lower or equal rate, then .add(ord, counter)
				orderBooks[currSell][currBuy].add(ord);
				// output(String.format(" orderBooks[%d][%d] length: %d", currSell, currBuy, orderBooks[currSell][currBuy].size()));
			}
		}

		// start with fast syncing until first trade, then be slow until user hits "F"
		if (numTrades == 1) {
			platform.setSleepAfterSync(delaySlowSync);
		}
	}

	@Override
	public void noMoreTransactions() {
	}

	@Override
	public synchronized void init(Platform platform, AddressBook addressBook) {
		this.platform = platform;
		this.addressBook = addressBook;
		this.numMembers = addressBook.getSize();

		try {
			Field field = platform.getClass().getDeclaredField("platformId");
			field.setAccessible(true);
			this.main_id = field.getInt(platform);
		} catch (Exception e) {}

		// use args to initialize tickerSymbol
		String[] pars = platform.getParameters(); // read parameters from config.txt
		numCurrencies = pars.length -1;
		tickerSymbol = new String[numCurrencies];
		tickerRate = new double[numCurrencies];
		long[] initialBalances = new long[numCurrencies];

		for (int i=0; i<numCurrencies; i++)
		{
			String[] parts = pars[i+1].split(":");
			tickerSymbol[i] = parts[0];
			tickerRate[i] = Double.parseDouble(parts[1]);
			initialBalances[i] = Math.round(divisor * Double.parseDouble(parts[2]));
		}

		portfolio = new long[numMembers][numCurrencies];
		trades = new String[MAX_TRADES];
		numTradesStored = 0;
		lastTradeIndex = 0;
		numTrades = 0;

		for (int i = 0; i < numMembers; i++) {
			portfolio[i] = new long[numCurrencies];
			for (int j = 0; j < numCurrencies; j++) {
				portfolio[i][j] = initialBalances[j]; // each member starts with predefined amount of currencies
			}
		}

    // initialize orderBooks
		this.orderBooks = (ArrayList<CryptoExchangeOrder>[][]) new ArrayList [numCurrencies][numCurrencies];
		for (int i = 0; i < numCurrencies; i++) {
			for (int j = 0; j < numCurrencies; j++) {
				if (i != j) {
					this.orderBooks[i][j] = new ArrayList<CryptoExchangeOrder>();
				}
			}
		}

		// start with fast syncing, until the first trade
		this.platform.setSleepAfterSync(delayFastSync);
	}

	private String describeOrder(CryptoExchangeOrder ord) {
		String sellerNickname = addressBook.getAddress(ord.origId)
				.getNickname();
		byte currSell = ord.sell;
		byte currBuy  = ord.buy;
		long amount   = ord.amount;
		double rate   = ord.rate;
		String orderDescription = String.format(
				"Order of %s: %f %3s -> %3s @ %f %s",
				sellerNickname, (double)amount/divisor, tickerSymbol[currSell], tickerSymbol[currBuy], (double)rate
				,ord.timeCreated);
		return orderDescription;
	}

	private String getPortfolio(long[] portfolio) {
		String result = "";
		for (int i=0; i<portfolio.length; i++)
		{
			if (result != "") {
				result += ", ";
			}
			result += String.format("%1$,.2f", 1.0*portfolio[i]/divisor) + this.tickerSymbol[i];
		}
		return result;
	}

	private void output(String str) {
		if (main_id==0) {
			System.out.println(str);
		}
	}
}
