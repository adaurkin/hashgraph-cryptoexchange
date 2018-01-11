
/*
 *
 */

import java.nio.ByteBuffer;
import java.time.Instant; // temp?
import java.time.temporal.ChronoUnit;

public class CryptoExchangeOrder {
  public byte type;
  public int origId;
  public byte sell;
  public byte buy;
  public long amount;
  public double rate;
  public Instant timeCreated; // temp?

  public CryptoExchangeOrder(byte t, byte s, byte b, long a, double r){
    type = t;
    sell = s;
    buy = b;
    amount = a;
    rate = r;
    // timeCreated = Instant.now();
    // System.out.println("CryptoExchangeOrder created: " + Instant.now().toString()); // temp
  }

  public CryptoExchangeOrder(int id, byte[] bufOrder){
    origId = id;
    ByteBuffer buf = ByteBuffer.wrap(bufOrder);
    type = buf.get();
		sell   = buf.get();
		buy    = buf.get();
		amount = buf.getLong();
		rate   = buf.getDouble();
  }

  public byte[] byteBuffer(){
    byte[] buf = ByteBuffer.allocate(1+1+1+8+8).put(type).put(sell).put(buy).putLong(amount).putDouble(rate).array();
    return buf;
  }

  /* returns age of the order in millisecond */
  public long age(){
    return timeCreated.until(Instant.now(), ChronoUnit.MILLIS);
  }
}
