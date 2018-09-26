package net.minecraftforge.installer;

import javax.swing.ProgressMonitor;

public interface IMonitor {
    void setSteps(int max);
    void setNote(String note);
    void setProgress(int progress);
    void close();

    public static IMonitor buildMonitor() {
        if (SimpleInstaller.headless) {
            return new IMonitor() {
                @Override public void setSteps(int max){}
                @Override public void setProgress(int progress){}
                @Override public void close(){}
                @Override
                public void setNote(String note) {
                    System.out.println("MESSAGE: "+ note);
                }
            };
        } else {
            return new IMonitor() {
                private ProgressMonitor monitor;
                {
                    monitor = new ProgressMonitor(null, "Downloading libraries", "Libraries are being analyzed", 0, 1);
                    monitor.setMillisToPopup(0);
                    monitor.setMillisToDecideToPopup(0);
                }
                @Override
                public void setSteps(int max) {
                    monitor.setMaximum(max);
                }

                @Override
                public void setNote(String note) {
                    System.out.println(note);
                    monitor.setNote(note);
                }

                @Override
                public void setProgress(int progress) {
                    monitor.setProgress(progress);
                }

                @Override
                public void close() {
                    monitor.close();
                }
            };
        }
    }
}
