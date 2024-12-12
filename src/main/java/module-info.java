module me.mdzs.drawtogether {
    requires javafx.controls;
    requires javafx.fxml;


    opens me.mdzs.drawtogether to javafx.fxml;
    exports me.mdzs.drawtogether;
}