package mesquite.augist.RearrangeStartTree;

/* Mesquite source code.  J.C. Oliver.  April 2010. 
Version 2.72, December 2009.
Disclaimer:  The Mesquite source code is lengthy and we are few.  There are no doubt inefficiencies and goofs in this code. 
The commenting leaves much to be desired. Please approach this source code with the spirit of helping out.
Perhaps with your help we can be more than a few, and make Mesquite better.

Mesquite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.
Mesquite's web site is http://mesquiteproject.org

This source code and its compiled class files are free and modifiable under the terms of 
GNU Lesser General Public License.  (http://www.gnu.org/copyleft/lesser.html)
 */

import java.util.*;
import java.awt.*;
import mesquite.lib.*;
import mesquite.lib.duties.*;
import mesquite.trees.lib.*;

/* ======================================================================== */
/**Almost identical to AddAndRearrange, but hires a OneTreeSource to serve as the starting tree.*/
public class RearrangeStartTree extends TreeSearcher implements Incrementable {
	public String getName() {
		return "Heuristic (Rearrange a user-designated starting tree)";
	}
	/*.................................................................................................................*/
	public String getExplanation() {
		return "Searches for optimal trees by rearranging a user-supplied tree.  For instance, the starting tree could be a tree with polytomies randomly resolved.";
	}
	/*.................................................................................................................*/
	public boolean requestPrimaryChoice(){
		return false;
	}
	/*.................................................................................................................*/
	public void getEmployeeNeeds(){  //This gets called on startup to harvest information; override this and inside, call registerEmployeeNeed
		EmployeeNeed e = registerEmployeeNeed(NumberForTree.class, getName() + "  needs a method to evaluate tree optimality.",
		"The method to evaluate tree optimality can be selected initially");
		EmployeeNeed ew = registerEmployeeNeed(TreeSwapper.class, getName() + "  needs a method to rearrange branches on the trees.",
		"The method to rearrange branches can be selected initially");
	}
	/*.................................................................................................................*/
	int currentTree=0;
	NumberForTree treeValueTask;
	static int numTrees = 1;
	boolean minimize = true;
	MesquiteString treeValueName;
	TreeSwapper swapTask;
	int MAXTREES = 100;
	TreeOptimizer treeOptimizer;
	TreeSource startingTreeSource;
	MesquiteString treeSourceName;
	int startingTreeCount = 0;
	/*.................................................................................................................*/
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		treeValueTask= (NumberForTree)hireEmployee(NumberForTree.class, "Criterion for tree search");
		if (treeValueTask==null) return sorry(getName() + " couldn't start because no criterion-calculating module was obtained");
		treeValueName = new MesquiteString(treeValueTask.getName());
		minimize = !treeValueTask.biggerIsBetter();
		swapTask = (TreeSwapper)hireEmployee(TreeSwapper.class, "Tree Rearranger");
		if (swapTask==null)
			return sorry(getName() + " couldn't start because no branch rearranger module was obtained");
		if (!MesquiteThread.isScripting()){
			int s = MesquiteInteger.queryInteger(containerOfModule(), "MAXTREES", "Maximum number of equally good trees to store during search (MAXTREES)", MAXTREES);
			if (MesquiteInteger.isCombinable(s))
				MAXTREES = s;
			else
				return false;
		}
		treeOptimizer = new TreeOptimizer(this,treeValueTask, swapTask);
		addMenuItem("MAXTREES...", makeCommand("setMAXTREES",  this));
		startingTreeSource = (TreeSource)hireEmployee(TreeSource.class, "Starting Tree Source");
		if(startingTreeSource == null) return sorry(getName() + " couldn't start because no starting tree was obtained.");
		treeSourceName = new MesquiteString(startingTreeSource.getName());
		return true;
		//TODO: if this is made to be persistent, need setHiringCommand for both
	}

	/*.................................................................................................................*/
	public Snapshot getSnapshot(MesquiteFile file) {
		Snapshot temp = new Snapshot();
		temp.addLine("setCriterion " , treeValueTask);
		temp.addLine("toggleMinimize " + minimize);
		temp.addLine("setMAXTREES " + MAXTREES);
		return temp;
	}
	MesquiteInteger pos = new MesquiteInteger();
	/*.................................................................................................................*/
	public Object doCommand(String commandName, String arguments, CommandChecker checker) {
		if (checker.compare(this.getClass(), "Sets criterion for tree search", "[name of module calculating objective function]", commandName, "setCriterion")) {
			NumberForTree temp= (NumberForTree)replaceEmployee(NumberForTree.class, arguments, "Criterion for tree choice", treeValueTask);
			if (temp!=null){
				treeValueTask=  temp;
				treeValueName.setValue(treeValueTask.getName());
				minimize = !treeValueTask.biggerIsBetter();
				parametersChanged(); //?
				return treeValueTask;
			}
		}
		else if (checker.compare(this.getClass(), "Sets the maximum number of trees stored", "[number]", commandName, "setMAXTREES")) {
			int s = MesquiteInteger.fromString(parser.getFirstToken(arguments));
			if (!MesquiteInteger.isCombinable(s)){
				s = MesquiteInteger.queryInteger(containerOfModule(), "MAXTREES", "Maximum number of equally good trees to store during search (MAXTREES)", MAXTREES);
			}
			if (MesquiteInteger.isCombinable(s)){
				MAXTREES = s;
				parametersChanged(); 
			}
		}
		else if (checker.compare(this.getClass(), "Toggles whether or not the score is to minimized or maximized", "[on = minimize; off = maximize]", commandName, "toggleMinimize")) {
			//TODO: use MesquiteBoolean checkmark
			if (StringUtil.blank(arguments))
				minimize = !minimize;
			else {
				String s = ParseUtil.getFirstToken(arguments, pos);
				if ("on".equalsIgnoreCase(s))
					minimize = true;
				else if  ("off".equalsIgnoreCase(s))
					minimize = false;
			}
			parametersChanged(); //?
		}
		else
			return  super.doCommand(commandName, arguments, checker);
		return null;
	}

	public void setCurrent(long i){  //SHOULD NOT notify (e.g., parametersChanged)
		if (treeValueTask instanceof Incrementable)
			((Incrementable)treeValueTask).setCurrent(i);
	}
	public long getCurrent(){
		if (treeValueTask instanceof Incrementable)
			return ((Incrementable)treeValueTask).getCurrent();
		return 0;
	}
	public String getItemTypeName(){
		if (treeValueTask instanceof Incrementable)
			return ((Incrementable)treeValueTask).getItemTypeName();
		return "";
	} 
	public long getMin(){
		if (treeValueTask instanceof Incrementable)
			return ((Incrementable)treeValueTask).getMin();
		return 0;
	}
	public long getMax(){
		if (treeValueTask instanceof Incrementable)
			return ((Incrementable)treeValueTask).getMax();
		return 0;
	}
	public long toInternal(long i){
		if (treeValueTask instanceof Incrementable)
			return ((Incrementable)treeValueTask).toInternal(i);
		return i-1;
	}
	public long toExternal(long i){ //return whether 0 based or 1 based counting
		if (treeValueTask instanceof Incrementable)
			return ((Incrementable)treeValueTask).toExternal(i);
		return i+1;
	}

	/*.................................................................................................................*/
	int whichNode = 0;
	MesquiteNumber bestValue;

	boolean better(MesquiteNumber value, MesquiteNumber bestValue){
		return ((minimize && value.isLessThan(bestValue)) || (!minimize && value.isMoreThan(bestValue)));
	}
	boolean justAsGood(MesquiteNumber value, MesquiteNumber bestValue){
		return ((minimize && !value.isMoreThan(bestValue)) || (!minimize && !value.isLessThan(bestValue)));
	}
	/*.................................................................................................................*/
	private TreeVector getTrees(Taxa taxa) {
		TreeVector trees = new TreeVector(taxa);
		logln("Tree search: Adding taxa");
		CommandRecord.tick("Tree Search in progress " );
		bestValue = new MesquiteNumber(0);
		ProgressIndicator progIndicator = new ProgressIndicator(getProject(),"Tree Search", "Tree Search in progress", 0, "Stop Search");
		progIndicator.setButtonMode(ProgressIndicator.FLAG_AND_HIDE);
		progIndicator.setText("Tree Search in progress ");
		progIndicator.setTertiaryMessage(getParameters());
		progIndicator.start();
		Runtime rt = Runtime.getRuntime();
		rt.gc();
	
		
		treeOptimizer.setSwapTask(swapTask);
		treeOptimizer.setNumberTask(treeValueTask);
		treeOptimizer.setBiggerIsBetter(!minimize);
		treeOptimizer.setProgIndicator(progIndicator);
		treeOptimizer.setWriteToLog(true);

		int numTrees = startingTreeSource.getNumberOfTrees(taxa, true);
		MesquiteTree initialTree = (MesquiteTree)(startingTreeSource.getTree(taxa, startingTreeCount)).cloneTree();
		if(numTrees == MesquiteInteger.infinite){
			startingTreeCount++;
		}

		treeValueTask.calculateNumber(initialTree, bestValue, null);

		trees.addElement(initialTree, false);
		if (minimize)
			initialTree.setName("Tree " + 1 + " from search (criterion: minimize " + treeValueTask.getName() + ")");
		else
			initialTree.setName("Tree " + 1 + " from search (criterion: maximimize " + treeValueTask.getName() + ")");

		if (progIndicator.isAborted()) {
			progIndicator.goAway();
			return null;
		}
		logln("Tree search: About to swap.  Score = "+ bestValue.toString() + "  Rearranging tree using " + swapTask.getName());
		String bestString = "Best tree found so far of score " + bestValue.toString();
		boolean firstWarning = true;
		long totalSwaps = 0;
		if (swapTask!=null) {
			CommandRecord rec = CommandRecord.getRecNSIfNull();
			for (int swapTree = 0; swapTree< trees.size(); swapTree++){
				MesquiteTree tree = (MesquiteTree)trees.getTree(swapTree);
				MesquiteTree sTree = tree.cloneTree();
				boolean better = true;
				while (better&& !progIndicator.isAborted() && !rec.isCancelled()) { 
					better = false;
					
					long swaps = swapTask.numberOfRearrangements(tree);
					MesquiteNumber value = new MesquiteNumber();
					for (int i = 0; i<swaps && !better && !progIndicator.isAborted() && !rec.isCancelled(); i++) {
						
						progIndicator.setSecondaryMessage(bestString + ";  Now examining rearrangement #" + i + " of " + swaps + " on tree " + (swapTree + 1));
						sTree.setToClone(tree);
						swapTask.rearrange(sTree, i);

						if (trees.indexOfByTopology(sTree, false)<0){ //new tree
							totalSwaps++;
							value.setToUnassigned();
							treeValueTask.calculateNumber(sTree, value, null);
							if (better(value, bestValue)) {
								bestValue.setValue(value);
								bestString = "Best tree found so far of score " + bestValue.toString();
								logln("    Tree search: Better tree found, score = " + value.toString());
								progIndicator.setSecondaryMessage("Better tree found, score = " + value.toString());
								tree.setToClone(sTree);
								trees.removeAllElements(false);
								trees.addElement(tree, false);
								if (minimize)
									tree.setName("Tree " + 1 + " from search (criterion: minimize " + treeValueTask.getName() + ")");
								else
									tree.setName("Tree " + 1 + " from search (criterion: maximimize " + treeValueTask.getName() + ")");
								better = true;
								swapTree = 0;
							}
							else if(justAsGood(value, bestValue) && trees.size()<MAXTREES){
								if (trees.size()>=MAXTREES){
									if (firstWarning){
										progIndicator.setTertiaryMessage("MAXTREES of " + MAXTREES + " hit in tree search.\n\n" + getParameters());
										logln(" MAXTREES of " + MAXTREES + " hit in tree search.");
									}
									firstWarning = false;
								}
								else {
									MesquiteTree sTree2 = sTree.cloneTree();
									trees.addElement(sTree2, false);
									bestString = Integer.toString(trees.size()) + " trees found so far of score " + bestValue.toString();
									progIndicator.setSecondaryMessage("Equally good tree found, score = " + value.toString());
									if (minimize)
										sTree2.setName("Tree " + trees.size() + " from search (criterion: minimize " + treeValueTask.getName() + ")");
									else
										sTree2.setName("Tree " + trees.size() + " from search (criterion: maximimize " + treeValueTask.getName() + ")");
									logln("    Tree search: Equally good tree found, total number of trees found = " + trees.size());
								}
							}
						}
					}
				}
			}
		}
		if (progIndicator.isAborted()) {
			if (!AlertDialog.query(containerOfModule(), "Keep trees?", "Tree search stopped before rearranging complete.  Best tree found has score = " + bestValue.toString() + ".  Do you want to keep the trees?", "Keep", "Discard", 1))
				return null;
			for (int i = 0; i< trees.size(); i++){
				MesquiteTree tree = (MesquiteTree)trees.getTree(i);
				if (minimize)
					tree.setName("Tree " + (i+1) + " from INCOMPLETE search (criterion: minimize " + treeValueTask.getName() + ")");
				else
					tree.setName("Tree " + (i+1) + " from INCOMPLETE search (criterion: maximimize " + treeValueTask.getName() + ")");
			}
		}
		else {
			logln("    Tree search: Swapping completed.  Best tree found has score = " + bestValue.toString() + ". Total number of rearrangements examined: " + totalSwaps);
		}
		if (progIndicator!=null)
			progIndicator.goAway();
		return trees;
	}
	/** Called to provoke any necessary initialization.  This helps prevent the module's intialization queries to the user from
   	happening at inopportune times (e.g., while a long chart calculation is in mid-progress)*/
	public boolean initialize(Taxa taxa){
		treeValueTask.initialize(taxa.getDefaultTree());
		return true;
	}
	/*.................................................................................................................*/
	public void fillTreeBlock(TreeVector treeList){
		if (treeList==null)
			return;
		Taxa taxa = treeList.getTaxa();
		TreeVector trees = getTrees(taxa);
		treeList.setName("Trees from Mesquite's heuristic search");
		treeList.setAnnotation ("Parameters: "  + getParameters(), false);
		if (trees!=null)
			treeList.addElements(trees, false);
	}
	/*.................................................................................................................*/
	public String getParameters() {
		if (minimize)
			return("Tree search criterion: minimize " + treeValueTask.getName());
		else
			return("Tree search criterion: maximimize " + treeValueTask.getName());
	}
	/*.................................................................................................................*/
	public boolean isPrerelease(){
		return false;
	}
	/*.................................................................................................................*/
	public boolean showCitation(){
		return true;
	}
	/*.................................................................................................................*/
	public boolean isSubstantial(){
		return false;
	}
}