/* Mesquite source code.  J.C. Oliver.  June 2010.
Disclaimer:  The Mesquite source code is lengthy and we are few.  There are no doubt inefficiencies and goofs in this code. 
The commenting leaves much to be desired. Please approach this source code with the spirit of helping out.
Perhaps with your help we can be more than a few, and make Mesquite better.

Mesquite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.
Mesquite's web site is http://mesquiteproject.org

This source code and its compiled class files are free and modifiable under the terms of 
GNU Lesser General Public License.  (http://www.gnu.org/copyleft/lesser.html)
*/
package mesquite.augist.PartitionVariance;

/*TODO: consider adding a frequency cutoff for calculations.  The number of bipartitions could become 
 * quite large for TreeBlocks of thousands of trees.  Calculations could be performed only in those 
 * cases where a bipartition was found in at least 5% of the trees in one (or both) of the sets of trees.*/

import mesquite.lib.*;
import mesquite.lib.duties.*;
import mesquite.augist.CmdMajRuleConsenser.*;
import mesquite.augist.lib.*;
import mesquite.consensus.lib.*;

import java.util.Vector;


import mesquite.consensus.MajRuleTree.*;

/**Calculates the partition variance for a tree block.  Partition variance is defined 
 * as the average variance in partition frequency in a sample of (two) consensus trees.  
 * For the purposes of this module, the consensus trees are from the first and latter 
 * halves of a user-defined tree block.  (Effectively the same as the MrBayes standard 
 * deviation of split frequencies, but calculated for variances, not standard deviations).
 * 
 * Because bipartition frequencies are harvested from the BipartitionVector.getDecimalFrequency 
 * method, which returns a double, there is a chance of imprecision around 10E-15 digits.  
 * For example, a bipartition found in 1 tree out of 50 may be reported as occurring at a frequency 
 * of 0.020000000000000007, instead of a frequency of 0.02.*/
public class PartitionVariance extends NumberForTreeBlock {
	CmdMajRuleConsenser treeConsenserOne;
	MajRuleTree treeForTesting;
	Vector<BipartitionInfo> varianceVector;
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		treeConsenserOne = (CmdMajRuleConsenser)hireNamedEmployee(CmdTreeConsenser.class, "#CmdMajRuleConsenser", null, true);
		if (treeConsenserOne == null){
			return sorry(getName() + " couldn't start because no tree consenser was obtained.");
		}
		varianceVector = new Vector<BipartitionInfo>();
		return true;
	}
	/*.................................................................................................................*/
	public Snapshot getSnapshot(MesquiteFile file) { 
		Snapshot temp = new Snapshot();
		temp.addLine("setConsenserOne ", treeConsenserOne);
		return temp;
	}
	/*.................................................................................................................*/
	public Object doCommand(String commandName, String arguments, CommandChecker checker) {
		if (checker.compare(this.getClass(), "Sets the module to calculate numbers for the trees", "[name of module]", commandName, "setConsenserOne")) {
			CmdMajRuleConsenser temp = (CmdMajRuleConsenser)replaceEmployee(CmdMajRuleConsenser.class, arguments, "Which consenser to use?", treeConsenserOne);
			if (temp!=null) {
				treeConsenserOne = temp;
				//treeConsenserOne.setHiringCommand(mc);   See AverageTreeValue if menu hiring commands are desired.
				parametersChanged(); //Required for calculations to be performed on startup.
				return treeConsenserOne;
			}
		}
		else return super.doCommand(commandName, arguments, checker);
		return null;
	}
	
	/*.................................................................................................................*/
	public void calculateNumber(TreeVector trees, MesquiteNumber result, MesquiteString resultString) {
		if (result==null)
			return;
	   	clearResultAndLastResult(result);
		int numTrees = trees.getNumberOfTrees();
		varianceVector.clear();
		//Checks to make sure at least four trees are in tree block.
		if(numTrees <= 4){
			result.setToUnassigned();
			logln(getName() + " can only be calculated on tree blocks containing 4 or more trees.  Partition variance not calculated for " + trees.getName() + " (only " + numTrees + " found in block).");
		}
		else{
			int halfwayPoint = 0;
			if (numTrees % 2 == 0){
				halfwayPoint = (int)(numTrees/2);
			}
			else{
				halfwayPoint = (int)((numTrees-1)/2);
			}

			TreeVector firstHalfTrees = new TreeVector(trees.getTaxa());
			TreeVector secondHalfTrees = new TreeVector(trees.getTaxa());

			for(int nTrees = 0; nTrees < numTrees; nTrees++){
				if(nTrees < halfwayPoint){
					firstHalfTrees.addElement(trees.getTree(nTrees), false);
				}
				else{
					secondHalfTrees.addElement(trees.getTree(nTrees), false);
				}
			}//end of for loop filling the two TreeVectors

			/*Get BipartitionVectors corresponding to each half of the TreeBlock.  Must use .clone() 
			 * method because otherwise BipartitionVector firstBiVec would be accessing the 
			 * bipartitions created when the second half of the tree block was used for consensus.*/
			
			treeConsenserOne.consense(firstHalfTrees);
			BipartitionVector firstBiVec = (BipartitionVector)treeConsenserOne.getBipartitions().clone();

			treeConsenserOne.reset(firstHalfTrees.getTaxa());

			treeConsenserOne.consense(secondHalfTrees);
			BipartitionVector secondBiVec = (BipartitionVector)treeConsenserOne.getBipartitions().clone();

			/*Booleans to make sure partitions aren't counted twice; will need to cycle 
			 * through and include those partitions which exist in only one set (first 
			 * or second half of TreeBlock) of trees.*/
			boolean[] firstSet = new boolean[firstBiVec.size()];
			for(int fs = 0; fs < firstSet.length; fs++){
				firstSet[fs] = false;
			}
			boolean[] secondSet = new boolean[secondBiVec.size()];
			for(int ss = 0; ss < secondSet.length; ss++){
				secondSet[ss] = false;
			}
			//Two longs to keep track of the number of bipartitions found, could probably be ints
			long firstFound = 0;
			long secondFound = 0;
			/*Temporary storage for each variance value to be added the the Vector of BipartitionInfo 
			 * elements.  All variance calculations are performed by a simplification of the calculation 
			 * for 2-sample variance: 0.5*x1^2 - x1*x2 + 0.5*x2^2, where x1 and x2 are the bipartition 
			 * frequencies from the first and second set of trees, respectively.*/
			double varianceHolder = MesquiteDouble.unassigned;

			//First, compare each bipartition from first set of trees to each bipartition of second set of trees
			for(int firstSetCounter=0; firstSetCounter<firstBiVec.size(); firstSetCounter++){
				for(int secondSetCounter=0; secondSetCounter<secondBiVec.size(); secondSetCounter++){
					varianceHolder = MesquiteDouble.unassigned;
					Bits bitsOne = firstBiVec.getBipart(firstSetCounter).getBits();
					Bits bitsTwo = secondBiVec.getBipart(secondSetCounter).getBits();
					if(bitsOne.equals(bitsTwo)){

						firstSet[firstSetCounter] = true;
						secondSet[secondSetCounter] = true;
						firstFound++;
						secondFound++;

						//See note at top above concerning the precision of these variables.
						double firstDecimalFreq = firstBiVec.getDecimalFrequency(firstBiVec.getBipart(firstSetCounter));
						double secondDecimalFreq = secondBiVec.getDecimalFrequency(secondBiVec.getBipart(secondSetCounter));

						if(MesquiteDouble.isCombinable(firstDecimalFreq) && MesquiteDouble.isCombinable(secondDecimalFreq)){
							varianceHolder = (0.5)*Math.pow(firstDecimalFreq,2) - (firstDecimalFreq)*(secondDecimalFreq) + (0.5)*Math.pow(secondDecimalFreq,2);
						}
						else logln("Non-combinable number found.");
						BipartitionInfo bipartInfo = new BipartitionInfo(bitsOne, varianceHolder);
						varianceVector.addElement(bipartInfo);
						
					}
				}
			}

			/*starting from last element in the first BipartitionVector, check for any bipartitions which were 
			 * found in first set of trees, but not the second set.  Starting from last element should increase 
			 * efficiency because high frequency bipartitions are more likely to be found in both sets 
			 * of trees AND the BipartitionVector may have sorted bipartitions in descending order (depending 
			 * on whether the group frequency table was printed via BipartitionVector.dump()).*/
			int firstSetToCheck = firstBiVec.size() - 1;
			while(firstSetToCheck >= 0 && firstFound < firstBiVec.size()){
				varianceHolder = MesquiteDouble.unassigned;
				if(firstSet[firstSetToCheck] == false){
					Bits bitsOne = firstBiVec.getBipart(firstSetToCheck).getBits();

					//See note at top above concerning the precision of this variable.
					double firstDecimalFreq = firstBiVec.getDecimalFrequency(firstBiVec.getBipart(firstSetToCheck));

					if(MesquiteDouble.isCombinable(firstDecimalFreq)){
						varianceHolder = (0.5)*Math.pow(firstDecimalFreq,2);
					}
					else logln("Non-combinable number found.");
					BipartitionInfo bipartInfo = new BipartitionInfo(bitsOne, varianceHolder);
					varianceVector.addElement(bipartInfo);
					firstFound++;
					firstSet[firstSetToCheck] = true;
				}
				firstSetToCheck--;
			}

			/*Starting from last element in the second BipartitionVector, check for any bipartitions which were 
			 * found in second set of trees, but not the first set.  Starting from last element should increase 
			 * efficiency because high frequency bipartitions are more likely to be found in both sets 
			 * of trees AND the BipartitionVector may have sorted bipartitions in descending order (depending 
			 * on whether the group frequency table was printed via BipartitionVector.dump()).*/
			int secondSetToCheck = secondBiVec.size() - 1;
			while(secondSetToCheck >= 0 && secondFound < secondBiVec.size()){
				varianceHolder = MesquiteDouble.unassigned;
				if(secondSet[secondSetToCheck] == false){
					Bits bitsTwo = secondBiVec.getBipart(secondSetToCheck).getBits();

					//See note at top above concerning the precision of this variable.
					double secondDecimalFreq = secondBiVec.getDecimalFrequency(secondBiVec.getBipart(secondSetToCheck));

					if(MesquiteDouble.isCombinable(secondDecimalFreq)){
						varianceHolder = (0.5)*Math.pow(secondDecimalFreq,2);
					}
					else logln("Non-combinable number found.");
					BipartitionInfo bipartInfo = new BipartitionInfo(bitsTwo, varianceHolder);
					varianceVector.addElement(bipartInfo);
					secondFound++;
					secondSet[secondSetToCheck] = true;
				}
				secondSetToCheck--;
			}

			double aveVar = 0.0;
			double sum = 0.0;
			int count = 0;
			for (int summingVariances = 0; summingVariances < varianceVector.size(); summingVariances++){
				if(varianceVector.elementAt(summingVariances) instanceof BipartitionInfo){
					sum += ((BipartitionInfo)(varianceVector.elementAt(summingVariances))).getVariance();
					count++;
				}
			}

			aveVar = (double)(sum/count);
			result.setValue(aveVar);
			if (resultString!=null){
				resultString.setValue("Average variance in bipartition frequencies: " + result.toString());
			}

			saveLastResult(result);
			saveLastResultString(resultString);
			
		}//end of else for tree blocks >= 4 trees
	}
	/*.................................................................................................................*/
	public void initialize(TreeVector trees) {
	}
	/*.................................................................................................................*/
	public String getName() {
		return "Partition Variance";
	}
	/*.................................................................................................................*/
	public String getExplanation(){
		return "Calculates the average variance in partition frequencies in consensus trees based on the first and second halves of a tree block.";
	}
	/*.................................................................................................................*/
	public boolean isSubstantial(){
		return false;
	}
	/*.................................................................................................................*/
	/** returns whether this module is requesting to appear as a primary choice */
  	public boolean requestPrimaryChoice(){
  		return false;
  	}
}

/**A local class to provide temporary storage for bipartition frequency variances.*/
class BipartitionInfo{
	Bits bits;
	double variance;
	public BipartitionInfo(Bits bipartitionBits, double bipartitionVar){
		bits = bipartitionBits;
		variance = bipartitionVar;
	}
	/*.................................................................................................................*/
	public Bits getBits(){
		return bits;
	}
	/*.................................................................................................................*/
	public double getVariance(){
		return variance;
	}
}