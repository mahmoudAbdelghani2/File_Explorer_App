import java.io.File;
import java.util.LinkedList;

public class FolderManager {
    private final LinkedList<File> folders;

    public FolderManager() {
        this.folders = new LinkedList<>();
    }

    public void addFolder(File folder) {
        if (folders.stream().noneMatch(f -> f.getAbsolutePath().equals(folder.getAbsolutePath()))) {
            folders.add(folder);
        }
    }

    public LinkedList<File> getFolders() {
        return folders;
    }

    public void removeFolder(File folder) {
        folders.remove(folder);
    }
}