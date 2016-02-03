package net.minecraftforge.installer;

import java.io.File;

import com.alee.managers.notification.NotificationIcon;
import com.alee.managers.notification.NotificationManager;
import com.alee.managers.notification.WebNotification;

public interface ActionType {
    boolean run(File target);
    boolean isPathValid(File targetDir);
    String getFileError(File targetDir);
    String getSuccessMessage();
    String getSponsorMessage();
    
    public static void error(String message){
    	final WebNotification notificationPopup = new WebNotification();
        notificationPopup.setIcon(NotificationIcon.error);
        notificationPopup.setDisplayTime(5000);
        notificationPopup.setContent(message);
        NotificationManager.showNotification(notificationPopup);
    }
}
