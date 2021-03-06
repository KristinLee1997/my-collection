/*
 * Copyright 2010 The Greplin Bloom Filter Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.king.learn.collection.mycollection.bloomfilter.demo7;

import com.king.learn.collection.mycollection.bloomfilter.demo7.allocator.CloseCallback;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Basic sanity checks on the bloom filter
 */
public class BloomFilterTest {
    private static final String[] IN = {"hello sweet world",
            "goodbye cruel world",
            "Bloomfilter",
            "what is with java and bit twiddling?"};

    private static final String[] OUT = {"and another one",
            "greplin got nominated for a crunchie!",
            "why? I dunno",
            "sdalkf sdkljfds"};

    private static final File TEMP_FILE;


    static {
        try {
            TEMP_FILE = File.createTempFile("greplin-bloom", "test");
        } catch (IOException e) {
            throw new RuntimeException("Unable to create new temp file", e);
        }
    }

    private static String printBits(byte x) {
        String s = Integer.toBinaryString(x & 0xff);
        return repeat(8 - s.length(), '0') + s;
    }

    private static String repeat(int count, char x) {
        String res = "";
        for (int i = 0; i < count; i++) {
            res += x;
        }

        return res;
    }

    @Test
    public void testClear() throws IOException {
        BloomFilter bf = BloomFilter.createOptimal(TEMP_FILE, 1000, 0.00001, true);
        bf.add("hello".getBytes());
        Assert.assertTrue(bf.contains("hello".getBytes()));
        Assert.assertFalse(bf.contains("goodbye".getBytes()));

        bf.clear();
        Assert.assertFalse(bf.contains("hello".getBytes()));
        Assert.assertFalse(bf.contains("goodbye".getBytes()));
    }

    @Test
    public void testBasic() throws IOException {
        BloomFilter bf = BloomFilter.createOptimal(TEMP_FILE, 1000, 0.00001, true);

        for (String s : IN) {
            bf.add(s.getBytes());
            Assert.assertTrue(bf.contains(s.getBytes()));
        }

        for (String s : OUT) {
            Assert.assertFalse(bf.contains(s.getBytes()));
        }

        for (String s : IN) {
            Assert.assertTrue(bf.contains(s.getBytes()));
        }
    }

    @Test
    public void testOpenClose() throws IOException {
        BloomFilter bf = BloomFilter.createOptimal(TEMP_FILE, 1000, 0.00001, true);

        for (String s : IN) {
            bf.add(s.getBytes());
            Assert.assertTrue(bf.contains(s.getBytes()));
        }

        for (String s : OUT) {
            Assert.assertFalse(bf.contains(s.getBytes()));
        }

        for (String s : IN) {
            Assert.assertTrue(bf.contains(s.getBytes()));
        }

        int originalCapacity = bf.capacity(0.00001);
        Assert.assertEquals(1000, originalCapacity);
        byte[] exactData = bf.getUnderlyingDataBytes();
        bf.close();
        bf = null;

        bf = BloomFilter.openExisting(TEMP_FILE);
        for (String s : OUT) {
            Assert.assertFalse(bf.contains(s.getBytes()));
        }

        for (String s : IN) {
            Assert.assertTrue(bf.contains(s.getBytes()));
        }

        Assert.assertArrayEquals(exactData, bf.getUnderlyingDataBytes());
        Assert.assertEquals(originalCapacity, bf.capacity(0.00001));
        bf.close();
    }

    @Test
    public void testSetNewFile() throws IOException {
        BloomFilter bf1 = BloomFilter.createOptimal(TEMP_FILE, 1000, 0.00001, true);
        BloomFilter bf2 = BloomFilter.createOptimal(null, 1000, 0.00001, true);

        for (String s : IN) {
            bf1.add(s.getBytes());
            bf2.add(s.getBytes());
            Assert.assertTrue(bf1.contains(s.getBytes()));
        }

        for (String s : OUT) {
            Assert.assertFalse(bf1.contains(s.getBytes()));
        }

        for (String s : IN) {
            Assert.assertTrue(bf1.contains(s.getBytes()));
        }

        byte[] exactData = bf1.getUnderlyingDataBytes();
        bf2.setNewFile(TEMP_FILE, true);

        for (String s : OUT) {
            Assert.assertFalse(bf2.contains(s.getBytes()));
        }

        for (String s : IN) {
            Assert.assertTrue(bf2.contains(s.getBytes()));
        }

        bf2.setNewFile(TEMP_FILE, true);

        for (String s : OUT) {
            Assert.assertFalse(bf2.contains(s.getBytes()));
        }

        for (String s : IN) {
            Assert.assertTrue(bf2.contains(s.getBytes()));
        }

        Assert.assertArrayEquals(exactData, bf2.getUnderlyingDataBytes());
        bf1.close();
        bf2.close();
    }

    @Test
    public void testMetadataOverwrite() throws IOException {
        // Ensures that metadata cannot be overwritten by hash data.

        BloomFilter bf = BloomFilter.createOptimal(TEMP_FILE, 1000, 0.00001, true);

        bf.add("".getBytes());
        Assert.assertTrue(bf.contains("".getBytes()));

        bf.flush();
        bf.close();

        bf = BloomFilter.openExisting(TEMP_FILE);

        Assert.assertTrue(bf.contains("".getBytes()));
    }

    @Test
    public void testSerialize() throws IOException {
        BloomFilter bf = BloomFilter.createOptimal(TEMP_FILE, 1000, 0.00001, true);

        for (String s : IN) {
            bf.add(s.getBytes());
            Assert.assertTrue(bf.contains(s.getBytes()));
        }

        for (String s : OUT) {
            Assert.assertFalse(bf.contains(s.getBytes()));
        }

        bf.flush();
        bf.close();

        bf = BloomFilter.openExisting(TEMP_FILE);

        for (String s : IN) {
            Assert.assertTrue(bf.contains(s.getBytes()));
        }

        for (String s : OUT) {
            Assert.assertFalse(bf.contains(s.getBytes()));
        }
    }

    @Test
    public void testSeekThreshold() throws IOException {
        int[] thresholdsToTest = {0, 1, 2, 5, 10, 100, 1000};
        for (int i : thresholdsToTest) {
            BloomFilter bf = new BloomFilter.NewBuilder(TEMP_FILE, 1000, 0.00001).force(true).seekThreshold(i)
                    .bucketSize(BucketSize.FOUR).build();

            for (String s : IN) {
                bf.add(s.getBytes());
                Assert.assertTrue(bf.contains(s.getBytes()));
            }

            for (String s : OUT) {
                Assert.assertFalse(bf.contains(s.getBytes()));
            }

            bf.flush();
            bf.close();

            bf = BloomFilter.openExisting(TEMP_FILE);

            for (String s : IN) {
                Assert.assertTrue(bf.contains(s.getBytes()));
            }

            for (String s : OUT) {
                Assert.assertFalse(bf.contains(s.getBytes()));
            }
        }
    }

    @Test
    public void testBrokenGetBucket() throws IOException {
        Assert.assertEquals(1, BloomFilter.getBucketAt((byte) 64, 1, 1));
    }

    @Test
    public void testBucketSizes() throws IOException {

        for (BucketSize bucketSize : BucketSize.values()) {
            BloomFilter bf = new BloomFilter.NewBuilder(TEMP_FILE, 1000, 0.00001).force(true)
                    .bucketSize(bucketSize).build();
            for (String s : IN) {
                bf.add(s.getBytes());
                Assert.assertTrue(bf.contains(s.getBytes()));
            }

            for (String s : OUT) {
                Assert.assertFalse(bf.contains(s.getBytes()));
            }

            for (String s : IN) {
                Assert.assertTrue(bf.contains(s.getBytes()));
                bf.remove(s.getBytes());

                if (bucketSize != BucketSize.ONE) { // can't remove items with bucket size of 1
                    Assert.assertFalse(bf.contains(s.getBytes()));
                }
            }
        }
    }

    @Test
    public void testCapacity() throws IOException {
        BloomFilter bf = BloomFilter.createOptimal(TEMP_FILE, 1000, 0.00001, true);
        Assert.assertEquals(1000, bf.capacity(0.00001));
    }

    @Test
    public void testRemove() throws IOException {
        BloomFilter bf = BloomFilter.createOptimal(TEMP_FILE, 1000, 0.00001, true);

        for (String s : IN) {
            bf.add(s.getBytes());
            Assert.assertTrue(bf.contains(s.getBytes()));
        }

        for (String s : OUT) {
            Assert.assertFalse(bf.contains(s.getBytes()));
        }


        for (String s : IN) {
            Assert.assertTrue(bf.contains(s.getBytes()));
        }

        for (int i = 0; i < IN.length; i++) {
            bf.remove(IN[i].getBytes());
            Assert.assertFalse(bf.contains(IN[i].getBytes()));

            for (int j = i + 1; j < IN.length; j++) {
                Assert.assertTrue(bf.contains(IN[j].getBytes()));
            }
        }

        for (String s : OUT) {
            Assert.assertFalse(bf.contains(s.getBytes()));
        }
        for (String s : IN) {
            Assert.assertFalse(bf.contains(s.getBytes()));
        }
    }

    @Test
    public void testFalsePositiveRate() throws IOException {

        for (BucketSize bucketSize : BucketSize.values()) {
            BloomFilter bf = new BloomFilter.NewBuilder(TEMP_FILE, 1000, 0.00001).force(true)
                    .bucketSize(bucketSize).build();

            Random r = new Random();

            for (int i = 0; i < 1000; i++) {
                byte[] item = new byte[100];
                r.nextBytes(item);
                bf.add(item);
                Assert.assertTrue("The item " + Arrays.toString(item) + "wasn't in the bloom filter (i = " + i + ")",
                        bf.contains(item));
            }

            int falsePositives = 0;
            // theoretically, we could generate they same random 100 bytes
            // that were previous inserted. but that's ludicrously unlikely
            for (int i = 0; i < 1000; i++) {
                byte[] item = new byte[100];
                r.nextBytes(item);
                if (bf.contains(item)) {
                    falsePositives += 1;
                }
            }

            // we expect 10 false positives. We should get more than 30 less than one in a million runs
            // see: http://pages.cs.wisc.edu/~cao/papers/summary-cache/node8.html
            Assert.assertTrue("We expect this test to fail around one in every million runs", falsePositives < 30);
        }
    }

    @Test
    public void testGetNumAt() throws IOException {
        final byte orig = 109; // 01101101

        Assert.assertEquals("01101101", printBits(orig));
        final byte lastFour = BloomFilter.getBucketAt(orig, 4, 4);
        Assert.assertEquals("00001101", printBits(lastFour));

        final byte firstFour = BloomFilter.getBucketAt(orig, 0, 4);
        Assert.assertEquals("00000110", printBits(firstFour));

        final byte wholeEight = BloomFilter.getBucketAt(orig, 0, 8);
        Assert.assertEquals(orig, wholeEight);
        Assert.assertEquals("01101101", printBits(wholeEight));

        final byte firstTwo = BloomFilter.getBucketAt(orig, 0, 2);
        Assert.assertEquals("00000001", printBits(firstTwo));

        final byte secondTwo = BloomFilter.getBucketAt(orig, 2, 2);
        Assert.assertEquals("00000010", printBits(secondTwo));

        final byte thirdTwo = BloomFilter.getBucketAt(orig, 4, 2);
        Assert.assertEquals("00000011", printBits(thirdTwo));

        final byte lastTwo = BloomFilter.getBucketAt(orig, 6, 2);
        Assert.assertEquals("00000001", printBits(lastTwo));

        Assert.assertEquals(0, BloomFilter.getBucketAt(orig, 0, 1));
        Assert.assertEquals(1, BloomFilter.getBucketAt(orig, 1, 1));
        Assert.assertEquals(1, BloomFilter.getBucketAt(orig, 2, 1));
        Assert.assertEquals(0, BloomFilter.getBucketAt(orig, 3, 1));
        Assert.assertEquals(1, BloomFilter.getBucketAt(orig, 4, 1));
        Assert.assertEquals(1, BloomFilter.getBucketAt(orig, 5, 1));
        Assert.assertEquals(0, BloomFilter.getBucketAt(orig, 6, 1));
        Assert.assertEquals(1, BloomFilter.getBucketAt(orig, 7, 1));
    }

    @Test
    public void testPutBucket() throws IOException {
        final byte orig = 109; // 01101101

        Assert.assertEquals("00101101", printBits(BloomFilter.putBucketAt(orig, 0, 2, (byte) 0)));
        Assert.assertEquals("01101101", printBits(BloomFilter.putBucketAt(orig, 0, 2, (byte) 1)));
        Assert.assertEquals("10101101", printBits(BloomFilter.putBucketAt(orig, 0, 2, (byte) 2)));
        Assert.assertEquals("11101101", printBits(BloomFilter.putBucketAt(orig, 0, 2, (byte) 3)));

        Assert.assertEquals("01001101", printBits(BloomFilter.putBucketAt(orig, 2, 2, (byte) 0)));
        Assert.assertEquals("01011101", printBits(BloomFilter.putBucketAt(orig, 2, 2, (byte) 1)));
        Assert.assertEquals("01101101", printBits(BloomFilter.putBucketAt(orig, 2, 2, (byte) 2)));
        Assert.assertEquals("01111101", printBits(BloomFilter.putBucketAt(orig, 2, 2, (byte) 3)));

        Assert.assertEquals("11101101", printBits(BloomFilter.putBucketAt(orig, 0, 1, (byte) 1)));
        Assert.assertEquals("00101101", printBits(BloomFilter.putBucketAt(orig, 1, 1, (byte) 0)));
        Assert.assertEquals("01001101", printBits(BloomFilter.putBucketAt(orig, 2, 1, (byte) 0)));
        Assert.assertEquals("01111101", printBits(BloomFilter.putBucketAt(orig, 3, 1, (byte) 1)));
        Assert.assertEquals("01100101", printBits(BloomFilter.putBucketAt(orig, 4, 1, (byte) 0)));
        Assert.assertEquals("01101001", printBits(BloomFilter.putBucketAt(orig, 5, 1, (byte) 0)));
        Assert.assertEquals("01101111", printBits(BloomFilter.putBucketAt(orig, 6, 1, (byte) 1)));
        Assert.assertEquals("01101100", printBits(BloomFilter.putBucketAt(orig, 7, 1, (byte) 0)));
    }

    @Test
    public void testCloseCallback() throws IOException {
        final AtomicInteger closeCallbackCalled = new AtomicInteger(0);
        final long buckets = BloomFilter.calculateOptimalBucketCount(1000, 0.00001);
        BloomFilter bf = new BloomFilter.NewBuilder(TEMP_FILE, 1000, 0.00001)
                .force(true)
                .bucketSize(BucketSize.FOUR)
                .closeCallback(new CloseCallback() {
                    @Override
                    public void close(byte[] cache) {
                        Assert.assertNotNull(cache);
                        Assert.assertEquals((int) Math.ceil((double) buckets / 2.0), cache.length);
                        closeCallbackCalled.incrementAndGet();
                    }
                }).build();
        bf.add("Hello World".getBytes());
        Assert.assertTrue(bf.contains("Hello World".getBytes()));
        bf.close();
        Assert.assertEquals(1, closeCallbackCalled.get());
    }

    @Test
    public void testSnapshotting() throws IOException {
        BloomFilter bf = new BloomFilter.NewBuilder(TEMP_FILE, 1000, 0.00001)
                .force(true)
                .bucketSize(BucketSize.FOUR)
                .build();

        final byte[] data = "hello world".getBytes();
        bf.add(data);
        Assert.assertTrue(bf.contains(data));

        File snapshotFile = File.createTempFile("bloom-snapshot", "test");
        bf.makeConsistentSnapshot(snapshotFile, true);

        BloomFilter snapshot = BloomFilter.openExisting(snapshotFile);

        Assert.assertArrayEquals(bf.getUnderlyingDataBytes(), snapshot.getUnderlyingDataBytes());
        Assert.assertEquals(bf.getHashFns(), snapshot.getHashFns());
        Assert.assertEquals(bf.getTotalLength(), snapshot.getTotalLength());

        Assert.assertTrue(bf.contains(data));

        snapshot.close();

        // make sure 'force' works as expected
        boolean exceptionThrown = false;
        try {
            bf.makeConsistentSnapshot(snapshotFile, false);
        } catch (IOException e) {
            exceptionThrown = true;
        }

        Assert.assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            bf.makeConsistentSnapshot(snapshotFile, true);
        } catch (IOException e) {
            exceptionThrown = true;
        }
        Assert.assertFalse(exceptionThrown);
    }
}
