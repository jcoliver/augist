package mesquite.augist.lib;

import mesquite.lib.*;
import mesquite.lib.characters.*;
import mesquite.lib.duties.*;
import mesquite.ancstates.RecAncestralStates.*;

public abstract class PairwiseHistory extends FileAssistantA {
	/**Source of tree(s) for reconstructions*/
	public TreeSource treeSourceTask;
	public MesquiteString treeSourceName;
	//two variables to deal with tree sources which can provide an infinite number of trees (see getNumTrees and setNumTrees methods).
	public boolean numTreesSet = false;
	public int numTrees = 100;
	public Taxa currentTaxa = null;
	public int treeCount; //For counting successful reconstructions

	/**Source of first history for reconstructions*/
	public CharHistorySource historyTaskOne;
	public MesquiteString historyTaskNameOne;
	public CharacterHistory historyOne;
	public int currentCharOne=0;
	public int lastCharOneRetrieved = -1;
	public long currentMappingOne = 0;
	public CharacterState csOne; //TODO: Could become local, restricted to reportBothHistories method?
	public CharacterState csOneParent;
	/**Provides temporary storage of results for a single tree; should only contain results for one tree at a time, appended to file if filePath!=null.*/
	public MesquiteString resultStringOne;

	/**Source of second history for reconstructions*/
	public CharHistorySource historyTaskTwo;
	public MesquiteString historyTaskNameTwo;
	public CharacterHistory historyTwo;
	public int currentCharTwo=0;
	public int lastCharTwoRetrieved = -1;
	public long currentMappingTwo = 0;
	public CharacterState csTwo;//TODO: Could become local, restricted to reportBothHistories method?
	public CharacterState csTwoParent;
	/**Provides temporary storage of results for a single tree; should only contain results for one tree at a time, appended to file if filePath!=null.*/
	public MesquiteString resultStringTwo;

	/*Export options*/
	String filePath;
	boolean pathExists;
	boolean includeTreeName = true;
	boolean includeHeaderRow = true;
	boolean includeTermTaxaNames = false;

	/*.................................................................................................................*/
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		Taxa taxa = getProject().chooseTaxa(containerOfModule(), "For which block of taxa do you want to export ancestral reconstructions?");
		if(taxa == null)
			return sorry(getName() + " couldn't start because taxa not obtained.");
		currentTaxa = taxa;

		/*Hire tree source*/
		treeSourceTask = (TreeSource)hireEmployee(TreeSource.class, "Source of trees for reconstruction");
		if(treeSourceTask == null)
			return sorry(getName() + " couldn't start because no source of trees for reconstructions obtained.");
		treeSourceName = new MesquiteString(treeSourceTask.getName());
		treeSourceTask.setPreferredTaxa(currentTaxa);

		/*Get reconstructor modules for first character*/
		historyTaskOne = (CharHistorySource)hireNamedEmployee(CharHistorySource.class, "#RecAncestralStates");
		if (historyTaskOne == null)
			historyTaskOne = (CharHistorySource)hireEmployee(CharHistorySource.class, "First source of character histories to trace");
		if (historyTaskOne == null) {
			return sorry(getName() + " couldn't start because first source of character histories not obtained.");
		}
		historyTaskNameOne = new MesquiteString(historyTaskOne.getName());
		
		/*Select which character to use from first history source employee*/
		if(historyTaskOne instanceof RecAncestralStates && !MesquiteThread.isScripting()){
			int tempCharOne = (((RecAncestralStates)historyTaskOne).characterSourceTask.queryUserChoose(currentTaxa, "number one for Pairwise history."));
			if(MesquiteInteger.isCombinable(tempCharOne)){
				currentCharOne = tempCharOne;
			}
		}

		/*Get reconstructor module for second character*/
		historyTaskTwo = (CharHistorySource)hireNamedEmployee(CharHistorySource.class, "#RecAncestralStates");
		if (historyTaskTwo == null)
			historyTaskTwo = (CharHistorySource)hireEmployee(CharHistorySource.class, "Second source of character histories to trace");
		if (historyTaskTwo == null) {
			return sorry(getName() + " couldn't start because second source of character histories not obtained.");
		}
		historyTaskNameTwo = new MesquiteString(historyTaskTwo.getName());

		/*Select which character to use from second history source employee*/
		if(historyTaskTwo instanceof RecAncestralStates && !MesquiteThread.isScripting()){
			int tempCharTwo = (((RecAncestralStates)historyTaskTwo).characterSourceTask.queryUserChoose(currentTaxa, "number two for Pairwise history."));
			if(MesquiteInteger.isCombinable(tempCharTwo)){
				currentCharTwo = tempCharTwo;
			}
		}

		/*Get file path information for saving results*/
		filePath = MesquiteFile.saveFileAsDialog("Name of file to save results?");
		if(filePath!=null){
			pathExists=true;
			if(!filePath.endsWith(".txt")){
				filePath = filePath + ".txt";
			}
		} else pathExists=false;
		
		if(reportHistories()){
			//endJob();
			if(!MesquiteThread.isScripting()){
				logln(treeCount + " trees analyzed by " + getName() + ".");
				if(pathExists){
					logln("Pairwise character history results saved to " + filePath);
				}
				else alert(getName() + " could not find the specified path, or no path was specified.  Results will be written to the Mesquite log.");
			}
			iQuit();
			return true;
		}
		else {
			logln(getName() + " cancelled or could not export histories.");
			return false;
		}
	}
	/*.................................................................................................................*/
	/**If pathExists, appends given string to the file; if the file path does not exist, it writes the string to the Mesquite Log.*/
	public void writeResultsToFile(String reconResult){
		if(pathExists){
			MesquiteFile.appendFileContents(filePath, reconResult, false);
		}
		else {
			logln(reconResult);
		}
	}
	/*.................................................................................................................*/
	public void prepareHistories(Tree treeOne, Tree treeTwo){
		int maxnum = historyTaskOne.getNumberOfHistories(treeOne);//the purpose of the following five lines is unclear...April.14.2010.Oliver
		currentTaxa = treeOne.getTaxa();
		if (currentCharOne>= maxnum)
			currentCharOne = maxnum-1;
		if (currentCharOne<0)
			currentCharOne = 0;
		/*Spot where information regarding which character to reconstruct is used (via currentChar)*/
		historyTaskOne.prepareHistory(treeOne, currentCharOne);
		long nummap = historyTaskOne.getNumberOfMappings(treeOne, currentCharOne);
		if (currentMappingOne>= nummap)
			currentMappingOne = nummap-1;
		if (currentMappingOne<0)
			currentMappingOne = 0;
		lastCharOneRetrieved = currentCharOne;

		maxnum = historyTaskTwo.getNumberOfHistories(treeTwo);//the purpose of the following five lines is unclear...April.14.2010.Oliver
		if (currentCharTwo>= maxnum)
			currentCharTwo = maxnum-1;
		if (currentCharTwo<0)
			currentCharTwo = 0;
		/*Spot where information regarding which character to reconstruct is used (via currentChar)*/
		historyTaskTwo.prepareHistory(treeTwo, currentCharTwo);
		nummap = historyTaskTwo.getNumberOfMappings(treeTwo, currentCharTwo);
		if (currentMappingTwo>= nummap)
			currentMappingTwo = nummap-1;
		if (currentMappingTwo<0)
			currentMappingTwo = 0;
		lastCharTwoRetrieved = currentCharTwo;
	}
	/*.................................................................................................................*/
	/**Queries user for number of trees, if tree source provides an infinite number of trees, such as SimulatedTrees.*/
	public int getNumTrees(){
		if (MesquiteThread.isScripting() || numTreesSet)
			return numTrees;
		int newNum = MesquiteInteger.queryInteger(containerOfModule(), "Set Number of Trees", "Number of Trees (for Export Ancestral Reconstructions):", numTrees, 0, MesquiteInteger.infinite);
		if (newNum>0) {
			numTrees = newNum;
			numTreesSet = true;
		}
		return numTrees;
	}
	/*.................................................................................................................*/
	public void setNumTrees(int nTrees){
		if (nTrees>0 && MesquiteInteger.isCombinable(nTrees)) {
			numTrees = nTrees;
			numTreesSet = true;
		}
	}
	/*.................................................................................................................*/
	public boolean doReconsOnTree(Tree treeForRecon){
		Tree treeForOne = treeForRecon.cloneTree();//Cloning may be unnecessary April.14.2010.Oliver
		Tree treeForTwo = treeForRecon.cloneTree();
		prepareHistories(treeForOne, treeForTwo);
		MesquiteString historyResultStringOne = new MesquiteString("");
		historyOne = getMappingOne(treeForRecon, historyResultStringOne);
		MesquiteString historyResultStringTwo = new MesquiteString("");
		historyTwo = getMappingTwo(treeForRecon, historyResultStringTwo);
		if(historyOne == null || historyTwo == null)
			return false;
		else return true;
	}
	/*.................................................................................................................*/
	public CharacterHistory getMappingOne(Tree tree, MesquiteString resultString){
		if (!MesquiteLong.isCombinable(currentMappingOne))
			currentMappingOne = 0;
		CharacterHistory currentHistory = historyTaskOne.getMapping(currentMappingOne, null, resultString);
		currentMappingOne++;
		currentHistory = historyTaskOne.getMapping(currentMappingOne, null, resultString);
		return currentHistory;
	}
	/*.................................................................................................................*/
	public CharacterHistory getMappingTwo(Tree tree, MesquiteString resultString){
		if (!MesquiteLong.isCombinable(currentMappingTwo))
			currentMappingTwo = 0;
		CharacterHistory currentHistory = historyTaskTwo.getMapping(currentMappingTwo, null, resultString);
		currentMappingTwo++;
		currentHistory = historyTaskTwo.getMapping(currentMappingTwo, null, resultString);
		return currentHistory;
	}
	/*.................................................................................................................*/
	public abstract boolean reportHistories();
}
