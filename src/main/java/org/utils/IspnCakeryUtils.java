package org.utils;

/**
 * Util class for Infinispan cakery.
 *
 * @author Tomas Sykora <tomas@infinispan.org>
 */
public class IspnCakeryUtils {

    /**
     * Return OData standardized JSON (represented as String)
     * This can be passed as content (StringEntity) of HTTP POST request
     * <p/>
     *
     * @param entityClass - just string json parameter
     * @param id - just string json parameter
     * @param gender - just string json parameter
     * @param firstName - just string json parameter
     * @param lastName - just string json parameter
     * @param age - just int json parameter
     * @return Standardized OData JSON person entity as String.
     */
    public static String createJsonPersonString(String entityClass, String id,
                                                String gender, String firstName, String lastName, int age) {

        StringBuilder sb = new StringBuilder();

        sb.append("{");
        sb.append("\"entityClass\":\"" + entityClass + "\",\n");
        sb.append("\"id\":\"" + id + "\",\n");
        sb.append("\"gender\":\"" + gender + "\",\n");
        sb.append("\"firstName\":\"" + firstName + "\",\n");
        sb.append("\"lastName\":\"" + lastName + "\",\n");

        // Source: http://www.javamex.com/tutorials/memory/string_memory_usage.shtml
        // 1 java char = 2 bytes
        // 100 000 chars = 200 000 bytes = approx. 200 KB
        // 10 000 entries x 200 KB = approx. 2 GB of data
        // or 20 000 entries with 50 000 chars = approx. 2 GB of data

        // or 100 000 entries with 10 000 chars (=20 KB) (1 large document) = approx. 2 GB of data

        // This is approximately 20 KB+ entry
//        char[] chars = new char[10000];
//        Arrays.fill(chars, 'x');
//        String payload = new String(chars);
//
//        sb.append("\"documentString\":\"" + payload + "\",\n");

        sb.append("\"age\":" + age + "\n");
        sb.append("}");

        return sb.toString();
    }

}
