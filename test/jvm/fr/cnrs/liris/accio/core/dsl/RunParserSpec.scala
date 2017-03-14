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

package fr.cnrs.liris.accio.core.dsl

import fr.cnrs.liris.accio.core.domain._
import fr.cnrs.liris.accio.core.framework.RunFactory
import fr.cnrs.liris.accio.core.storage.memory.MemoryWorkflowRepository
import fr.cnrs.liris.dal.core.api.Values
import fr.cnrs.liris.testing.UnitSpec

/**
 * Unit tests for [[RunParser]].
 */
class RunParserSpec extends UnitSpec {
  private[this] val myWorkflow = Workflow(
    id = WorkflowId("my_workflow"),
    version = "v1",
    owner = User("me"),
    isActive = true,
    createdAt = System.currentTimeMillis(),
    graph = GraphDef(Set(
      NodeDef(
        op = "FirstSimple",
        name = "FirstSimple",
        inputs = Map("foo" -> InputDef.Value(Values.encodeInteger(42)))),
      NodeDef(
        op = "SecondSimple",
        name = "SecondSimple",
        inputs = Map(
          "dbl" -> InputDef.Value(Values.encodeDouble(3.14)),
          "data" -> InputDef.Reference(Reference("FirstSimple", "data")))))),
    name = Some("my workflow"))

  private[this] val parser = {
    val mapper = new ObjectMapperFactory().create()
    val workflowRepository = new MemoryWorkflowRepository()
    workflowRepository.save(myWorkflow)
    new RunParser(mapper, workflowRepository, new RunFactory(workflowRepository))
  }

  it should "parse a minimal run definition" in {
    val spec = parser.parse("""{"workflow": "my_workflow"}""", Map.empty)
    spec.pkg.workflowId shouldBe myWorkflow.id
    spec.pkg.workflowVersion shouldBe "v1"
    spec.owner shouldBe None
    spec.name shouldBe None
    spec.notes shouldBe None
    spec.tags shouldBe Set.empty
    spec.seed shouldBe None
    spec.params shouldBe Map.empty
    spec.repeat shouldBe None
    spec.clonedFrom shouldBe None
  }

  it should "parse a more complete run definition" in {
    val spec = parser.parse(
      """{"workflow": "my_workflow",
        |"name": "named run",
        |"notes": "All my notes",
        |"tags": ["my", "awesome", "run"],
        |"seed": 1234567890123,
        |"repeat": 15}""".stripMargin,
      Map.empty)
    spec.pkg.workflowId shouldBe myWorkflow.id
    spec.pkg.workflowVersion shouldBe "v1"
    spec.owner shouldBe None // There is never an owner from definition.
    spec.name shouldBe Some("named run")
    spec.notes shouldBe Some("All my notes")
    spec.tags shouldBe Set("my", "awesome", "run")
    spec.seed shouldBe Some(1234567890123L)
    spec.params shouldBe Map.empty
    spec.repeat shouldBe Some(15)
    spec.clonedFrom shouldBe None // Cloned from is not supported ATM.
  }

  it should "detect an invalid workflow" in {
    assertErrors(
      """{"workflow": "unknown_workflow"}""",
      InvalidSpecMessage("Workflow not found: unknown_workflow"))

    assertErrors(
      """{"workflow": "invalid:workflow:identifier"}""",
      InvalidSpecMessage("Invalid workflow specification: invalid:workflow:identifier"))
  }

  private def assertErrors(content: String, errors: InvalidSpecMessage*) = {
    val expected = intercept[InvalidSpecException] {
      parser.parse(content, Map.empty)
    }
    expected.errors should contain theSameElementsAs errors
  }
}
