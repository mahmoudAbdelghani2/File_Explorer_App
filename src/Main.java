import javafx.application.Application;
import javafx.stage.Stage;

public class Main extends Application {

    @Override
    public void start(Stage primaryStage) {
        MainView mainView = new MainView();
        primaryStage.setScene(mainView.getScene());
        primaryStage.setTitle("File Explorer");
        primaryStage.setWidth(800);
        primaryStage.setHeight(600);
        primaryStage.setResizable(false);
        mainView.setDefaultSorting();

        primaryStage.setOnCloseRequest(e -> mainView.shutdown());

        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}