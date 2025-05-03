import java.util.ArrayList;
import java.util.List;

public class Sorter {
    public static void mergeSort(List<FileItem> items, String sortBy) {
        if (items == null || items.size() <= 1) return;

        int mid = items.size() / 2;
        List<FileItem> left = new ArrayList<>(items.subList(0, mid));
        List<FileItem> right = new ArrayList<>(items.subList(mid, items.size()));

        mergeSort(left, sortBy);
        mergeSort(right, sortBy);

        merge(items, left, right, sortBy);
    }

    private static void merge(List<FileItem> result, List<FileItem> left, List<FileItem> right, String sortBy) {
        int i = 0, j = 0, k = 0;

        while (i < left.size() && j < right.size()) {
            if (compare(left.get(i), right.get(j), sortBy) <= 0) {
                result.set(k++, left.get(i++));
            } else {
                result.set(k++, right.get(j++));
            }
        }

        while (i < left.size()) result.set(k++, left.get(i++));
        while (j < right.size()) result.set(k++, right.get(j++));
    }

    private static int compare(FileItem a, FileItem b, String sortBy) {
        boolean aIsDir = a.getFile().isDirectory();
        boolean bIsDir = b.getFile().isDirectory();

        if (aIsDir && !bIsDir) return -1;
        if (!aIsDir && bIsDir) return 1;

        switch (sortBy.toLowerCase()) {
            case "name":
                return a.getName().compareToIgnoreCase(b.getName());
            case "size":
                return Long.compare(a.getSize(), b.getSize());
            case "extension":
                if (aIsDir && bIsDir) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
                int extCompare = a.getExtension().compareToIgnoreCase(b.getExtension());
                if (extCompare == 0) {
                    return a.getName().compareToIgnoreCase(b.getName());
                }
                return extCompare;
            default:
                return a.getName().compareToIgnoreCase(b.getName());
        }
    }
}