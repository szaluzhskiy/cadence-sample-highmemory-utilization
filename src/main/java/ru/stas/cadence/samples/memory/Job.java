package ru.stas.cadence.samples.memory;

public class Job implements Runnable {

  @Override
  public void run() {

//    System.out.println("start create payload");
//    int batchSize = 10;
//    int limit =  1024;
//    byte[] arr = new byte[batchSize*limit];
//    for (int i = 0; i < limit; i++) {
//      arr[i*batchSize] = 0;
//      arr[i*batchSize+1] = 1;
//      arr[i*batchSize+2] = 2;
//      arr[i*batchSize+3] = 3;
//      arr[i*batchSize+4] = 4;
//      arr[i*batchSize+5] = 5;
//      arr[i*batchSize+6] = 6;
//      arr[i*batchSize+7] = 7;
//      arr[i*batchSize+8] = 8;
//      arr[i*batchSize+9] = 9;
//    }
//    System.out.println("finish create payload");
//    while (true) {
//      System.out.println("add payload");
//      byte[] memoryFillIntVar = Arrays.copyOf(arr, arr.length);
//      System.out.println(" Free Mem: " + Runtime.getRuntime().freeMemory());
//      try {
//        System.out.println("sleep");
//        Thread.sleep(5);
//      } catch (InterruptedException e) {
//        e.printStackTrace();
//      }
    int dummyArraySize = 1;
    System.out.println("Max JVM memory: " + Runtime.getRuntime().maxMemory());
    long memoryConsumed = 0;
    try {
      int[] memoryAllocated = null;
      for (int loop = 0; loop < Integer.MAX_VALUE; loop++) {
        memoryAllocated = new int[dummyArraySize];
        memoryAllocated[0] = 0;
        memoryConsumed += dummyArraySize * Integer.SIZE;
        System.out.println("Memory Consumed till now: " + memoryConsumed);
        dummyArraySize *= dummyArraySize * 2;
        Thread.sleep(5000);
      }
    } catch (InterruptedException e) {
      System.out.println(e);
    }
  }

  class Payload {
    byte[] payload;

    public Payload(byte[] payload) {
      this.payload = payload;
    }

    public byte[] getPayload() {
      return payload;
    }

    public void setPayload(byte[] payload) {
      this.payload = payload;
    }
  }
}
