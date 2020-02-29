package com.github.medavox.kotban;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

/**from https://stackoverflow.com/a/41767014*/
public class Main extends Application {
    private ScrollPane sp;
    private Timeline scrolltimeline = new Timeline();
    private double scrollVelocity = 0;

    boolean dropped;

    //Higher speed value = slower scroll.
    int speed = 200;

    @Override
    public void start(Stage primaryStage) throws Exception {
        BorderPane root = new BorderPane();

        sp = new ScrollPane();
        sp.setPrefSize(300, 300);

        VBox outer = new VBox(sp);

        VBox innerBox = new VBox();
        innerBox.setPrefSize(200,1000);

        sp.setContent(innerBox);

        root.setCenter(outer);

        Label dragMe = new Label("drag me to edge!\n"+"or drop me in scrollpane!");
        root.setTop(dragMe);

        setupScrolling();

        dragMe.setOnDragDetected((MouseEvent event) ->{  
            Dragboard db = dragMe.startDragAndDrop(TransferMode.ANY); 
            db.setDragView(((Node) event.getSource()).snapshot(null, null));  

            ClipboardContent content = new ClipboardContent();           
            content.putString((dragMe.getText()));

            db.setContent(content);
            event.consume();      
        });


        Scene scene = new Scene(root, 640, 480);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void setupScrolling() {
        scrolltimeline.setCycleCount(Timeline.INDEFINITE);
        scrolltimeline.getKeyFrames().add(new KeyFrame(Duration.millis(20), (ActionEvent) -> { dragScroll();}));

        sp.setOnDragExited((DragEvent event) -> {

            if (event.getY() > 0) {
                scrollVelocity = 1.0 / speed;
            }
            else {
                scrollVelocity = -1.0 / speed;
            }
            if (!dropped){
                scrolltimeline.play();
            }

        });

        sp.setOnDragEntered(event -> {
            scrolltimeline.stop();
            dropped = false;
        });
        sp.setOnDragDone(event -> {
            System.out.print("test");
            scrolltimeline.stop();
        });           
        sp.setOnDragDropped((DragEvent event) ->{      
            Dragboard db = event.getDragboard();
            ((VBox) sp.getContent()).getChildren().add(new Label(db.getString())); 
            scrolltimeline.stop();
            event.setDropCompleted(true);
            dropped = true;


        });

        sp.setOnDragOver((DragEvent event) ->{
            event.acceptTransferModes(TransferMode.MOVE);                            
            });


        sp.setOnScroll((ScrollEvent event)-> {
            scrolltimeline.stop();   
        });

        sp.setOnMouseClicked((MouseEvent)->{
            System.out.println(scrolltimeline.getStatus());

        });

    }
    private void dragScroll() {
        ScrollBar sb = getVerticalScrollbar();
        if (sb != null) {
            double newValue = sb.getValue() + scrollVelocity;
            newValue = Math.min(newValue, 1.0);
            newValue = Math.max(newValue, 0.0);
            sb.setValue(newValue);
        }
    }

    private ScrollBar getVerticalScrollbar() {
        ScrollBar result = null;
        for (Node n : sp.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar) {
                ScrollBar bar = (ScrollBar) n;
                if (bar.getOrientation().equals(Orientation.VERTICAL)) {
                    result = bar;
                }
            }
        }        
        return result;
    }

    public static void main(String[] args) {
        launch(args);
    }
}