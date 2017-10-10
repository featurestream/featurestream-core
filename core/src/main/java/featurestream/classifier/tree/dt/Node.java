/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package featurestream.classifier.tree.dt;

import featurestream.data.Instance;

import java.io.Serializable;
import java.util.Arrays;
import java.util.Collection;

/**
 * Represents an abstract node of a decision tree
 */
public abstract class Node implements Serializable {
	
	private static final long serialVersionUID = 1L;

	public enum Type {
    LEAF,
    NUMERICAL,
    CATEGORICAL
  }

  public long count;
  public double sum;
  public int[] label_counts;

  Node parent;
  public double G;
  public int n_samples;
  public int attr;

  // children
  protected Node left;
  protected Node right;
  protected double split;
 
  public int getAttr() { 
	  return attr;
  }

  public Node getParent() {
	  return parent;
  }
  
  public void setParent(Node parent) {
	  this.parent = parent;
  }
  
  public int getDepth() {
	  int depth=0;
	  Node curr = this;
	  while (curr != null) {
 		  depth++;
 		  curr = curr.parent;
	  }
	  return depth;
  }
  
  // replace the pointer to oldchild with one to newchild
  public void replaceChild(Node oldChild, Node newChild) {
	  
	  if (left == oldChild) {
		  left = newChild;
		  newChild.parent = this;
	  }
	  else if (right == oldChild) {
		  right = newChild;
		  newChild.parent = this;
	  }
	  else
		  assert false;
  }

  public int maxDepth() {
    return 1 + Math.max(left.maxDepth(), right.maxDepth());
  }
  
  public long nbNodes() {
    return 1 + left.nbNodes() + right.nbNodes();
  }
  
  public Node(Node parent, int attr, double split, Node left, Node right) {
	  setParent(parent);
	  this.attr = attr;
	  this.split = split;
	  this.left = left;
	  this.right = right;

	  // node statistics
	  G = Double.NaN;
	  n_samples = 0;
	  count = 0;
	  // for regression
	  sum = 0.0;
	  // for classification
	  label_counts = new int[2]; // FIXME use a sparse vector
  }
  

  /**
   * predicts the label for the instance
   * 
   * @return Double.NaN if the label cannot be predicted
   */
/*  public double classify(Instance instance) {
	Node n = walkTree(instance);
	if (n == null || n == this)
		return Double.NaN;
	else
		return n.classify(instance);
  }
  
  public Vector fullClassify(Instance instance) {
	Node n = walkTree(instance);
	if (n == null || n == this)
		return null;
	else
		return n.fullClassify(instance);
  }
*/
  
  /**
   * walks down the tree for the instance
   * @param instance the instance
   * @param update_counts if true then the n_sample counts are incremented along the path
   * @param stop_on_missing if true then the process terminates if a missing attribute is encountered
   * @return the last node the instance reaches
   */
  public abstract Node walkTree(Instance instance, boolean update_counts, boolean stop_on_missing);

  public abstract Type getType();

  public Collection<Node> getChildren() {
	  return Arrays.asList(new Node[]{left,right});
  }

  public String toString() {
	  return toString(1);
  }

  public String toString(int t) {
	  StringBuffer s = new StringBuffer();
      s.append(String.format("Node [attr=%d, type=%s, split=%f, sum=%f, count=%d, label_counts=%s]"
              ,attr,getType(),split,sum,count,Arrays.toString(label_counts)));
      if (getChildren()!=null) {
          s.append('\n');
		  for (Node c : getChildren()) {
			  for (int i=0;i<t;i++)
				  s.append("- ");
			  s.append(c.toString(t+1)).append('\n');
		  }
	  }
	  return s.toString();
  }
}
