package de.dal33t.powerfolder.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.ref.SoftReference;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.output.ByteArrayOutputStream;

/**
 * Helper class which serializes and deserializes java objects into byte arrays
 * 
 * @author <a href="mailto:totmacher@powerfolder.com">Christian Sprajc </a>
 * @version $Revision: 1.14 $
 */
public class ByteSerializer {
    private static final Logger LOG = Logger.getLogger(ByteSerializer.class);
    private static final int MAX_BUFFER_SIZE = 10 * 1024 * 1024;

    private SoftReference<ByteArrayOutputStream> outBufferRef;
    private SoftReference<byte[]> inBufferRef;

    public ByteSerializer() {
    }

    /**
     * Serialize an object. This method is non-static an re-uses the internal
     * byteoutputstream
     * 
     * @param target
     *            The object to be serialized
     * @param compress
     *            true if serialization should compress.
     * @param padToSize
     *            the size to pad the output buffer to. basically adds addio
     * @return The serialized object
     * @throws IOException
     *             In case the object cannot be serialized
     */
    public byte[] serialize(Serializable target, boolean compress, int padToSize)
        throws IOException
    {
        ByteArrayOutputStream byteOut;
        // Reset buffer
        if (outBufferRef != null && outBufferRef.get() != null) {
            // Reuse old buffer
            byteOut = outBufferRef.get();
            byteOut.reset();
        } else {
            LOG.verbose("Creating send buffer (512bytes)");
            // Create new bytearray output, 512b buffer
            byteOut = new ByteArrayOutputStream(512);
            // Chache outgoing buffer
            outBufferRef = new SoftReference<ByteArrayOutputStream>(byteOut);
        }

        OutputStream targetOut;
        // Serialize....
        if (compress) {
            PFZIPOutputStream zipOut = new PFZIPOutputStream(byteOut);
            targetOut = zipOut;
        } else {
            targetOut = byteOut;
        }
        ObjectOutputStream objOut = new ObjectOutputStream(targetOut);

        // Write
        objOut.writeObject(target);
        objOut.flush();
        byteOut.flush();
        objOut.close();

        if (padToSize > 0) {
            int modulo = byteOut.size() % padToSize;
            if (modulo != 0) {
                int additionalBytesRequired = padToSize - (modulo);
                // LOG.warn("Buffersize: " + byteOut.size()
                // + ", Additonal bytes required: " + additionalBytesRequired);
                for (int i = 0; i < additionalBytesRequired; i++) {
                    byteOut.write(0);
                }
            }
        }
        byteOut.flush();
        byteOut.close();

        if (byteOut.size() >= 128 * 1024) {
            LOG.warn("Send buffer exceeds 128KB! "
                + Format.formatBytes(byteOut.size()) + ". Message: " + target);
        }

        return byteOut.toByteArray();
    }

    /**
     * Re-uses internal received buffer for incoming readings.
     * 
     * @param in
     *            the input stream to deserialize from
     * @param expectedSize
     *            the expected size
     * @throws IOException
     * @return the deserialized byte array. the array might be bigger than
     *         expected size.
     */
    public byte[] read(InputStream in, int expectedSize) throws IOException {
        byte[] byteIn = null;
        if (inBufferRef != null && inBufferRef.get() != null) {
            // Re-use old buffer
            byteIn = inBufferRef.get();
        }

        if (expectedSize > MAX_BUFFER_SIZE) {
            LOG.error("Max buffersize overflow while reading. expected size "
                + expectedSize);
            return null;
        }

        // Check buffer
        boolean bufferExceeded = false;
        if (byteIn == null || byteIn.length < expectedSize) {
            if (LOG.isVerbose()) {
                String action = (byteIn == null) ? "Creating" : "Extending";
                LOG.verbose(action + " receive buffer ("
                    + Format.formatBytes(expectedSize) + ")");
            }
            byteIn = new byte[expectedSize];
            if (byteIn.length >= 128 * 1024) {
                bufferExceeded = true;
            }
            // Chache buffer
            inBufferRef = new SoftReference(byteIn);
        }

        // Read into receivebuffer
        StreamUtils.read(in, byteIn, 0, expectedSize);

        // Decrypt.
        if (bufferExceeded) {
            LOG.warn("Recived buffer exceeds 128KB! "
                + Format.formatBytes(byteIn.length));
        }

        return byteIn;
    }

    // Static serialization ***************************************************

    /**
     * Serialize an object
     * 
     * @param target
     *            The object to be serialized
     * @param compress
     *            true if the stream should be compressed.
     * @return The serialized object
     * @throws IOException
     *             In case the object cannot be serialized
     */
    public static byte[] serializeStatic(Serializable target, boolean compress)
        throws IOException
    {
        OutputStream targetOut;
        // Serialize....

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        if (compress) {
            PFZIPOutputStream zipOut = new PFZIPOutputStream(byteOut);
            targetOut = zipOut;
        } else {
            targetOut = byteOut;
        }
        ObjectOutputStream out = new ObjectOutputStream(targetOut);

        out.writeObject(target);
        out.flush();
        out.close();

        byte[] result = byteOut.toByteArray();
        /*
         * System.out.println( "Serialize size is: " + result.length + " Bytes,
         * compress: " + COMPRESS_STREAM);
         */
        return result;
    }

    /**
     * Deserialize a byte[] array into an Object.
     * 
     * @param base
     *            The byte[] array
     * @param expectCompression
     *            if there is a zip compression expected
     * @return The deserialized object
     * @throws IOException
     *             an I/O Error occured
     * @throws ClassNotFoundException
     *             the class for the Object to be deserialized cannot be found.
     */
    public static Object deserializeStatic(byte[] base,
        boolean expectCompression) throws IOException, ClassNotFoundException
    {

        /*
         * System.out.println( "De-Serialize size is: " + base.length + " Bytes,
         * compressed: " + COMPRESS_STREAM);
         */
        Object result;
        try {
            result = deserialize0(base, expectCompression);
        } catch (InvalidClassException e) {
            throw e;
        } catch (IOException e) {
            // System.err
            // .println("WARNING: Stream is not as expected, compression: "
            // + expectCompression + ". " + e.toString());
            // e.printStackTrace();
            // Maybe the stream is uncompressed/compressed
            // try the other way, if that also fails
            // we should forget it
            result = deserialize0(base, !expectCompression);
        }
        return result;
    }

    /**
     * Deserializer method with flag indicating if the base array is zip
     * compressed
     * 
     * @param base
     * @param compressed
     * @return the dezerialized object
     * @throws IOException
     * @throws ClassNotFoundException
     */
    private static Object deserialize0(byte[] base, boolean compressed)
        throws IOException, ClassNotFoundException
    {
        ObjectInputStream in = null;
        try {
            InputStream targetIn;
            // deserialize from the array.......u
            ByteArrayInputStream bin = new ByteArrayInputStream(base);
            if (compressed) {
                GZIPInputStream zipIn = new GZIPInputStream(bin);
                targetIn = zipIn;
            } else {
                targetIn = bin;
            }

            in = new ObjectInputStream(targetIn);
            Object result = in.readObject();
            return result;
        } finally {
            if (in != null) {
                in.close();
            }
        }
    }

    // Helper code ************************************************************

    public static String printBytes(byte[] bytes) {
        return bytes[0] + ":" + bytes[1] + ":" + bytes[2] + ":" + bytes[3]
            + " (" + bytes.length + ")";
    }
}