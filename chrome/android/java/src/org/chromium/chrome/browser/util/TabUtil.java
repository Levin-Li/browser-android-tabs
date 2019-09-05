package org.chromium.chrome.browser.util;

import android.view.View;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.app.Activity;
import android.content.Context;

import org.chromium.chrome.R;
import org.chromium.base.ApplicationStatus;
import org.chromium.chrome.browser.ChromeActivity;

public class TabUtil {
	public static void showTabPopupMenu(Context context,View view) {
		ChromeActivity chromeActivity = getChromeActivity();
		//Creating the instance of PopupMenu  
        PopupMenu popup = new PopupMenu(context, view);  
        //Inflating the Popup using xml file  
        popup.getMenuInflater().inflate(R.menu.new_tab_menu, popup.getMenu());

        if (chromeActivity != null && chromeActivity.getCurrentTabModel().isIncognito()) {
        	popup.getMenu().findItem(R.id.new_tab_menu_id).setVisible(false);
        }
        //registering popup with OnMenuItemClickListener  
        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {  
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                int id = item.getItemId();
                if (chromeActivity !=null) 
                    if (id == R.id.new_tab_menu_id){
                        openNewTab(chromeActivity, false);
                    } else if (id == R.id.new_incognito_tab_menu_id) {
                        openNewTab(chromeActivity, true);
                    }
                return true; 
            }  
        });
        popup.show();//showing popup menu
	}

	private static void openNewTab(ChromeActivity chromeActivity, boolean isIncognito) {
		chromeActivity.getTabModelSelector().getModel(isIncognito).commitAllTabClosures();
        chromeActivity.getTabCreator(isIncognito).launchNTP();
	}

	private static ChromeActivity getChromeActivity() {
      for (Activity ref : ApplicationStatus.getRunningActivities()) {
          if (!(ref instanceof ChromeActivity)) continue;
          return (ChromeActivity)ref;
      }
      return null;
    }
}