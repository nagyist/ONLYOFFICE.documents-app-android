package app.editors.manager.mvp.views.main;;

import java.util.ArrayList;

import app.editors.manager.mvp.models.explorer.File;
import moxy.viewstate.strategy.OneExecutionStateStrategy;
import moxy.viewstate.strategy.StateStrategyType;

@StateStrategyType(OneExecutionStateStrategy.class)
public interface DocsCloudView extends DocsBaseView {

    void onFileWebView(File file);

    void showMoveCopyDialog(ArrayList<String> names, String action, String title);

    void onOpenCoauthoringFile(File file, String serverString);
}
