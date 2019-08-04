package j2k;

import java.util.List;
import java.util.Arrays;

public class JavaJSONConverter<T> {
    public static void main(String[] args) {
        System.out.println("JSON: " + new JavaJSONConverter<Integer>().toJSONArray(Arrays.asList(98, 23, 34)));
    }

    public String toJSONArray(List<? extends T> list) {
        StringBuilder str = new StringBuilder("[");
        int size = list.size();
        for (int i = 0; i < size; i++) {
            str.append(list.get(i));
            if (i != (size - 1)) {
                str.append(", ");
            }
        }
        return str.append("]").toString();
    }
}
