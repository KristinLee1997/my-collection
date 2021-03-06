package com.king.learn.collection.mycollection.bloomfilter.demo7;

/**
 * Used for the Bloom filter. To simulate having multiple hash functions, we just take the linear combination
 * of two runs of the MurmurHash (https://www.eecs.harvard.edu/~michaelm/postscripts/tr-02-05.pdf says this is alright).
 * The core hashOnce fn is just a port of the C++ MurmurHash at http://code.google.com/p/smhasher/
 */
public class RepeatedMurmurHash {
    private final int hashCount;
    private final int max;

    public RepeatedMurmurHash(int count, int max) {
        hashCount = count;
        this.max = max;
    }

    private static int hashOnce(byte[] data, int seed) {
        int len = data.length;
        int m = 0x5bd1e995;
        int r = 24;

        int h = seed ^ len;
        int chunkLen = len >> 2;

        for (int i = 0; i < chunkLen; i++) {
            int iChunk = i << 2;
            int k = data[iChunk + 3];
            k = k << 8;
            k = k | (data[iChunk + 2] & 0xff);
            k = k << 8;
            k = k | (data[iChunk + 1] & 0xff);
            k = k << 8;
            k = k | (data[iChunk + 0] & 0xff);
            k *= m;
            k ^= k >>> r;
            k *= m;
            h *= m;
            h ^= k;
        }

        int lenMod = chunkLen << 2;
        int left = len - lenMod;

        if (left != 0) {
            if (left >= 3) {
                h ^= (int) data[len - 3] << 16;
            }
            if (left >= 2) {
                h ^= (int) data[len - 2] << 8;
            }
            if (left >= 1) {
                h ^= (int) data[len - 1];
            }

            h *= m;
        }

        h ^= h >>> 13;
        h *= m;
        h ^= h >>> 15;

        return h;
    }

    public int[] hash(byte[] data) {
        int[] result = new int[this.hashCount];

        int hashA = hashOnce(data, 0);
        int hashB = hashOnce(data, hashA);

        for (int i = 0; i < this.hashCount; i++) {
            result[i] = Math.abs((hashA + i * hashB) % max);
        }

        return result;
    }

}
