/**
 * 
 */
package net.minecraftforge.installer;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import com.alee.extended.drag.FileDragAndDropHandler;
import com.alee.extended.filechooser.FilesSelectionListener;
import com.alee.extended.filechooser.WebFileDrop;
import com.alee.extended.filechooser.WebFilePlate;
import com.alee.utils.CollectionUtils;

public class CustomWebFileDrop extends WebFileDrop {
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 5526008523558722244L;
	protected final List<FilesSelectionListener> uiListeners = new ArrayList<FilesSelectionListener>();

	/**
	 * 
	 */
	public CustomWebFileDrop() {
		super();
		setTransferHandler ( new FileDragAndDropHandler ()
        {
            /**
			 * 
			 */
			private static final long serialVersionUID = -7541377661487548403L;

			@Override
            public boolean isDropEnabled ()
            {
                return filesDropEnabled;
            }

            @Override
            public boolean filesDropped ( final List<File> files )
            {
                // Adding dragged files
                addSelectedFiles ( files );
                fireUISelectionChanged ();
                return true;
            }
        } );
	}
	
	public void addUIFileSelectionListener ( final FilesSelectionListener listener )
    {
		uiListeners.add ( listener );
    }
	
	public void removeUIFileSelectionListener ( final FilesSelectionListener listener )
    {
		uiListeners.remove ( listener );
    }

    protected void fireUISelectionChanged ()
    {
        for ( final FilesSelectionListener listener : CollectionUtils.copy ( uiListeners ) )
        {
        	listener.selectionChanged ( CollectionUtils.copy ( super.selectedFiles ) );
        }
    }
    
    protected WebFilePlate createFilePlate ( final File file )
    {
        final WebFilePlate filePlate = new WebFilePlate ( file );
        filePlate.setShowFileExtensions ( showFileExtensions );

        // To block parent container events
        addMouseListener ( new MouseAdapter ()
        {
            @Override
            public void mousePressed ( final MouseEvent e )
            {
                requestFocusInWindow ();
            }
        } );

        // Data change on removal
        filePlate.addCloseListener ( new ActionListener ()
        {
            @Override
            public void actionPerformed ( final ActionEvent e )
            {
                // Removing file from added files list
                selectedFiles.remove ( file );

                // Inform that selected files changed
                fireSelectionChanged();
                fireUISelectionChanged ();
            }
        } );

        return filePlate;
    }

}
