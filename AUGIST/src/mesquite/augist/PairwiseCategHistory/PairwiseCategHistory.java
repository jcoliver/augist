package mesquite.augist.PairwiseCategHistory;
/* Mesquite source code.  J.C. Oliver.  April 2010.
Disclaimer:  The Mesquite source code is lengthy and we are few.  There are no doubt inefficiencies and goofs in this code. 
The commenting leaves much to be desired. Please approach this source code with the spirit of helping out.
Perhaps with your help we can be more than a few, and make Mesquite better.

Mesquite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.
Mesquite's web site is http://mesquiteproject.org

This source code and its compiled class files are free and modifiable under the terms of 
GNU Lesser General Public License.  (http://www.gnu.org/copyleft/lesser.html)
 */

import mesquite.ancstates.RecAncestralStates.RecAncestralStates;
import mesquite.augist.lib.PairwiseHistory;
import mesquite.categ.lib.*;
import mesquite.lib.*;
import mesquite.lib.characters.*;
import mesquite.lib.duties.*;

/**Module to report history of two categorical characters reconstructed on a tree.  Designed to work with multiple trees and stochastic character mapping module.
* Reports transition histories for a pair of categorical characters.  Transition matrix for each tree is reported as a single, tab-separated line, column by column; where rows represent transitions in the first character and columns represent transitions in the second character.  For example, with a pair of two-state characters, the transition frequency matrix:
<table>
<tr><td></td><td colspan=5 align=center>Character Two</td></tr>
<tr><td rowspan=5 valign=middle>Character One</td><td></td><td>0&rarr;0</td><td>0&rarr;1</td><td>1&rarr;0</td><td>1&rarr;1</td></tr>
<tr><td>0&rarr;0</td><td align=center>&bull;</td><td align=center>&bull;</td><td align=center>&bull;</td><td align=center>&bull;</td></tr>
<tr><td>0&rarr;1</td><td align=center>&bull;</td><td align=center>&bull;</td><td align=center>&bull;</td><td align=center>&bull;</td></tr>
<tr><td>1&rarr;0</td><td align=center>&bull;</td><td align=center>&bull;</td><td align=center>&bull;</td><td align=center>&bull;</td></tr>
<tr><td>1&rarr;1</td><td align=center>&bull;</td><td align=center>&bull;</td><td align=center>&bull;</td><td align=center>&bull;</td></tr>
</table>

is displayed as:
<br>
0&rarr;0, 0&rarr;0&nbsp;0&rarr;1, 0&rarr;0&nbsp;1&rarr;0, 0&rarr;0&nbsp;1&rarr;1, 0&rarr;0&nbsp;0&rarr;0, 0&rarr;1&nbsp;&bull;&bull;&bull;<i>i<sub>max</sub></i>&rarr;<i>i<sub>max</sub></i>, <i>j<sub>max</sub></i>&rarr;<i>j<sub>max</sub></i>
<br>
where <i>i<sub>max</sub></i> is the maximum observed state in the first character and <i>j<sub>max</sub></i> is the maximum observed state in the second character.
*/
public class PairwiseCategHistory extends PairwiseHistory {
	/**Matrix of transition frequencies*/
	int[][] transitionArray;
	/**boolean for initial setup of transition matrix*/
	boolean arrayInitialized = false;
	int maxOne = 0;
	int maxTwo = 0;
	int numRows = 0;
	int numCols = 0;
	/**Header for results file*/
	String header = new String("Tree\t");

	/*.................................................................................................................*/
	public boolean reportHistories(){
		if(!(((RecAncestralStates)historyTaskOne).characterSourceTask.getCharacter(currentTaxa, currentCharOne).getCharacterDataClass() == CategoricalData.class)){
			return sorry(getName() + " requires categorical data, " + ((((RecAncestralStates)historyTaskOne).characterSourceTask).getName() + " is " + (((RecAncestralStates)historyTaskOne).characterSourceTask.getCharacter(currentTaxa, currentCharOne).getCharacterDataClass()).getName()));
		}			
		if(!(((RecAncestralStates)historyTaskTwo).characterSourceTask.getCharacter(currentTaxa, currentCharTwo).getCharacterDataClass() == CategoricalData.class)){
			return sorry(getName() + " requires categorical data, " + ((((RecAncestralStates)historyTaskTwo).characterSourceTask).getName() + " is " + (((RecAncestralStates)historyTaskTwo).characterSourceTask.getCharacter(currentTaxa, currentCharTwo).getCharacterDataClass()).getName()));
		}			
			
		int numTreesInSource = treeSourceTask.getNumberOfTrees(currentTaxa);
		boolean en = !MesquiteInteger.isFinite(numTreesInSource);
		if (en)
			numTrees = getNumTrees();
		else
			setNumTrees(numTreesInSource);
		/**Tree for reconstructing histories of two characters*/
		Tree treeForRecon;

		resultStringOne = new MesquiteString();
		resultStringTwo = new MesquiteString();
		treeCount = 0;

		/**Strings for progressIndicator reporting*/
		String charOneName = ((RecAncestralStates)historyTaskOne).characterSourceTask.getCharacterName(currentTaxa, currentCharOne);
		String charTwoName = ((RecAncestralStates)historyTaskTwo).characterSourceTask.getCharacterName(currentTaxa, currentCharTwo);
		String modelOneName = historyTaskOne.getNameAndParameters();
		String modelTwoName = historyTaskTwo.getNameAndParameters();
		
		ProgressIndicator progIndicator = new ProgressIndicator(getProject(), "Reporting Pairwise Histories" , "Surveying trees for pairwise character histories", numTrees, "Stop Reporting");
		if (progIndicator!=null){
			progIndicator.setButtonMode(ProgressIndicator.OFFER_CONTINUE);
			progIndicator.setOfferContinueMessageString("Are you sure you want to stop the survey?");
			progIndicator.setTertiaryMessage("Recording cohistories for: \n\tCharacter One: " + charOneName + ", using " + modelOneName + "\n\tCharacter Two: " + charTwoName + ", using " + modelTwoName);
			progIndicator.start();
		}
		/*Here is where, looping over all trees, reconstructions are completed & written to the output file (or log, if pathExists == false)*/
		for(int it = 0; it < numTrees; it++){//perform reconstructions for each tree, writing results to file for each tree
			if (progIndicator != null) {
				if (progIndicator.isAborted()) {
					progIndicator.goAway();
					if(!MesquiteThread.isScripting()){
						return sorry(getName() + " cancelled by user.  Pairwise histories were not reported.");
					}
					else return false;
				}
				progIndicator.setText("Tree " + it);
				progIndicator.setSecondaryMessage("Recording history on tree " + it + " of " + numTrees + " trees.");
				progIndicator.setCurrentValue(it);
			}
			resultStringOne.setValue("");//resetting so only current tree's results are included in the string.
			resultStringTwo.setValue("");//resetting so only current tree's results are included in the string.  This and preceding line may not be necessary. April.12.2010.Oliver
			treeForRecon = treeSourceTask.getTree(currentTaxa, it);
			if(doReconsOnTree(treeForRecon)){
				/*Array intialization.  Should occur only once, to setup header and size of transition matrix*/
				if(!arrayInitialized){
					maxOne = ((CategoricalHistory)historyOne).getMaxState();
					maxTwo = ((CategoricalHistory)historyTwo).getMaxState();

					numRows = (int)(Math.pow((maxOne + 1), 2));
					numCols = (int)(Math.pow((maxTwo + 1), 2));
					transitionArray = new int [numRows][numCols];
					for (int jStart = 0; jStart <=maxTwo; jStart++){
						for (int jEnd = 0; jEnd <=maxTwo; jEnd++){
							for (int iStart = 0; iStart <= maxOne; iStart++){
								for (int iEnd = 0 ;iEnd <= maxOne; iEnd++){
									header+=iStart + "->" + iEnd + ", " + jStart + "->" + jEnd;
									header+="\t";
								}
							}
						}
					}
					arrayInitialized = true;
					writeResultsToFile(header + "\n");
				}
				/*Zeroing all elements of transitionArray for current tree; confirmed that this works properly April.12.2010.Oliver*/
				for(int iOne = 0; iOne < numRows; iOne++){
					for(int iTwo = 0; iTwo < numCols; iTwo++){
						transitionArray[iOne][iTwo] = 0;
					}
				}
				int rootNode = treeForRecon.getRoot();
				/*Starting at root, recursing through tree, harvesting transition frequencies & storing in transitionArray*/
				summarizeCoHistories(treeForRecon,rootNode);

				String resultsForTree = treeForRecon.getName() + " (chars " + currentCharOne + ", " + currentCharTwo + "):";
				for (int iTwoFilled = 0; iTwoFilled < numCols; iTwoFilled++){
					for (int iOneFilled = 0; iOneFilled < numRows; iOneFilled++){
						resultsForTree += "\t" + transitionArray[iOneFilled][iTwoFilled];
					}
				}
				resultsForTree += "\n";
				writeResultsToFile(resultsForTree);
				treeCount++;
			}
		}
		if (progIndicator!=null) 
			progIndicator.goAway();
		return true;
	}
	/*.................................................................................................................*/
	/**For debugging purposes only*/
	private void printTransitionArray(){
		Debugg.println("");
		for(int column = 0; column < transitionArray.length; column++){
			for(int row = 0; row <transitionArray[column].length; row++){
				if(row > 0){
					Debugg.print("\t");
				}
				Debugg.print(transitionArray[row][column] + "");
			}
			Debugg.println("");
		}
	}
	/*.................................................................................................................*/
	private void summarizeCoHistories(Tree tree, int node){
		for (int daughter = tree.firstDaughterOfNode(node); tree.nodeExists(daughter); daughter = tree.nextSisterOfNode(daughter)){
			summarizeCoHistories(tree, daughter);
		}
		if(node != tree.getRoot()){
			int parent = tree.motherOfNode(node);
			csOne = historyOne.getCharacterState(csOne, node);
			csOneParent = historyOne.getCharacterState(csOneParent, parent);
			csTwo = historyTwo.getCharacterState(csTwo, node);
			csTwoParent = historyTwo.getCharacterState(csTwoParent, parent);

			/*only report transition in absence of uncertainty*/
			if(((CategoricalState)csOne).cardinality() == 1 && ((CategoricalState)csOneParent).cardinality() == 1 && ((CategoricalState)csTwo).cardinality() == 1 && ((CategoricalState)csTwoParent).cardinality() == 1){
				//A cludge to get states in 0, 1, 2... format instead of bits...
				int stateOne = ((CategoricalState)csOne).maximum(((CategoricalState)csOne).getValue());
				int stateOneParent = ((CategoricalState)csOneParent).maximum(((CategoricalState)csOneParent).getValue());
				int stateTwo = ((CategoricalState)csTwo).maximum(((CategoricalState)csTwo).getValue());
				int stateTwoParent = ((CategoricalState)csTwoParent).maximum(((CategoricalState)csTwoParent).getValue());

				int rowNumber = (stateOneParent * (maxOne + 1) + stateOne);
				int columnNumber = (stateTwoParent * (maxTwo + 1) + stateTwo);
	
				transitionArray[rowNumber][columnNumber]++;
			}
		}
	}
	/*.................................................................................................................*/
	public String getName() {
		return "Report Categorical Pairwise Histories";
	}
	/*.................................................................................................................*/
	public String getExplanation(){
		return "Reports transition histories for a pair of categorical characters.";
	}
	/*.................................................................................................................*/
	public boolean isPrerelease(){
		return true;
	}
	/*.................................................................................................................*/
	public boolean isSubstantive(){
		return true;
	}
}
