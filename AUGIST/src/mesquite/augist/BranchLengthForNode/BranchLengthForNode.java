package mesquite.augist.BranchLengthForNode;

import mesquite.lib.MesquiteString;
import mesquite.lib.NumberArray;
import mesquite.lib.Tree;
import mesquite.lib.duties.NumbersForNodes;

//Near copy of NodeDepth
public class BranchLengthForNode extends NumbersForNodes {

	/*.................................................................................................................*/
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		return true;
	}
	/*.................................................................................................................*/
	/** Called to provoke any necessary initialization.  This helps prevent the module's intialization queries to the user from
	happening at inopportune times (e.g., while a long chart calculation is in mid-progress)*/
	public void initialize(Tree tree) {
	}
	/*.................................................................................................................*/
	public void visitNodes(int node, Tree tree, NumberArray result) {
		result.setValue(node, tree.getBranchLength(node));
		for (int d = tree.firstDaughterOfNode(node); tree.nodeExists(d); d = tree.nextSisterOfNode(d)){ 
			visitNodes(d, tree, result);
		}
	}
	/*.................................................................................................................*/
	public void calculateNumbers(Tree tree, NumberArray result,	MesquiteString resultString) {
	 	if (result==null)
	 		return;
	clearResultAndLastResult(result);
	if (resultString!=null)
		resultString.setValue("");
	if (tree == null )
		return;

	visitNodes(tree.getRoot(), tree, result);
	saveLastResult(result);
	saveLastResultString(resultString);
	}
	/*.................................................................................................................*/
	/** A very short name for menus.
	 * @return	The name of the module*/
	public String getName() {
		return "Branch Length";
	}
	/*.................................................................................................................*/
	public boolean isPrerelease(){
		return true;
	}
}
