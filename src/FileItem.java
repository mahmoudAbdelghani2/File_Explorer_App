import java.io.File;

public class FileItem {
    private final File file;
    private final String name;
    private final String extension;
    private long size;

    public FileItem(File file) {
        this.file = file;
        this.name = file.getName();
        this.size = file.length();
        this.extension = file.isDirectory() ? "" : getFileExtension(file);
    }

    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return lastDot == -1 ? "" : name.substring(lastDot + 1).toLowerCase();
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getName() {
        return name;
    }

    public String getExtension() {
        return extension;
    }

    public long getSize() {
        return size;
    }

    public File getFile() {
        return file;
    }

    @Override
    public String toString() {
        return name;
    }
}