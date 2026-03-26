module com.osproject.dashboard {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.jcraft.jsch;

    opens com.osproject.dashboard to javafx.fxml;
    exports com.osproject.dashboard;
}
