package net.minecraftforge.installer;

import java.awt.Toolkit;
import java.io.File;

import javax.swing.JOptionPane;

//import com.alee.managers.notification.NotificationIcon;
//import com.alee.managers.notification.NotificationManager;
//import com.alee.managers.notification.WebNotification;

public interface ActionType {
    boolean run(File target);
    boolean isPathValid(File targetDir);
    String getFileError(File targetDir);
    String getSuccessMessage();
    String getSponsorMessage();
    
    public static void error(String message){
    	/*final WebNotificationPopup notificationPopup = new WebNotificationPopup();
        notificationPopup.setIcon(NotificationIcon.error);
        notificationPopup.setDisplayTime(5000);
        notificationPopup.setContent(message);
        NotificationManager.showNotification(notificationPopup);*/
    	Toolkit.getDefaultToolkit().beep();
    	JOptionPane.showMessageDialog(null, message, "Error!", JOptionPane.ERROR_MESSAGE);
    }
}
