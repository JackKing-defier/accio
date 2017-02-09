/*
 * Accio is a program whose purpose is to study location privacy.
 * Copyright (C) 2016-2017 Vincent Primault <vincent.primault@liris.cnrs.fr>
 *
 * Accio is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Accio is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Accio.  If not, see <http://www.gnu.org/licenses/>.
 */

namespace java fr.cnrs.liris.accio.core.domain

include "fr/cnrs/liris/dal/core/api/dal.thrift"
include "fr/cnrs/liris/accio/core/domain/common.thrift"
include "fr/cnrs/liris/accio/core/domain/operator.thrift"

/**
 * Definition of where the value of an input comes from.
 */
union InputDef {
  // If the input value comes from the value of a workflow parameter.
  1: string param;

  // If the input value comes from the output of another node.
  2: common.Reference reference;

  // If the input value is statically fixed.
  3: dal.Value value;
}

/**
 * Definition of a node inside a graph.
 */
struct NodeDef {
  // Operator name.
  1: required string op;

  // Node name.
  2: required string name;

  // Inputs of the operator. Only required inputs (i.e., those non-optional and without a default value) have to be
  // specified here, others can be omitted.
  3: required map<string, InputDef> inputs;
}

/**
 * Definition of a graph.
 */
struct GraphDef {
  // Definition of nodes forming this graph.
  1: required set<NodeDef> nodes;
}

/**
 * A workflow is a basically named graph of operators. A workflow can define parameters, which are workflow-level
 * inputs allowing to override the value of some node inputs at runtime.
 *
 * Workflows are versioned, which allows runs to reference them even if they change afterwards. Version identifiers
 * do not have to be incrementing integers, which allows to use things such as sha1.
 */
struct Workflow {
  // Workflow unique identifier. It is referenced by users creating runs, so it can be a little descriptive and not
  // totally random.
  1: required common.WorkflowId id;

  // Version identifier, unique among all version of a particular workflow. Besires this, there is no constraint on
  // it, it is just a plain string.
  2: required string version;

  // Whether this object represents the active (i.e., latest) version of the workflow.
  3: required bool is_active;

  // Time at which this version of the workflow was created.
  4: required common.Timestamp created_at;

  // Human-readable name.
  5: optional string name;

  // User owning this workflow (usually the one who created it).
  6: required common.User owner;

  // Graph definition.
  7: required GraphDef graph;

  // Workflow parameters.
  8: required set<operator.ArgDef> params;
}

struct WorkflowSpec {
  1: required common.WorkflowId id;
  2: optional string version;
  3: optional string name;
  4: optional common.User owner;
  5: required GraphDef graph;
  6: required set<operator.ArgDef> params;
}