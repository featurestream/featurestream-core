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

/**
 * Represents a node that splits using a numerical attribute
 */
public class NumericalNode extends Node {

  public NumericalNode(Node parent, int attr, double split, Node left,
			Node right) {
		super(parent, attr, split, left, right);
	}

  @Override
  public Node walkTree(Instance instance, boolean update_counts, boolean stop_on_missing) {
	  if (update_counts)
		  n_samples++;
	  if (attr >= instance.getAttrs().size() || Double.isNaN(instance.get(attr))) {
		  // missing
		  if (stop_on_missing)
			  return this;
		  else
			  return left.walkTree(instance, update_counts, stop_on_missing);			  
	  }
	  else if (instance.get(attr) <= split)
		  return left.walkTree(instance, update_counts, stop_on_missing);
	  else
		  return right.walkTree(instance, update_counts, stop_on_missing);
  }

  @Override
  public Type getType() {
    return Type.NUMERICAL;
  }

}
