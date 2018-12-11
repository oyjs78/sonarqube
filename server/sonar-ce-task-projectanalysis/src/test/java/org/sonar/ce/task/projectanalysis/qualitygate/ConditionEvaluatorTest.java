/*
 * SonarQube
 * Copyright (C) 2009-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.ce.task.projectanalysis.qualitygate;

import com.google.common.collect.ImmutableSet;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.sonar.ce.task.projectanalysis.measure.Measure;
import org.sonar.ce.task.projectanalysis.metric.Metric;
import org.sonar.ce.task.projectanalysis.metric.MetricImpl;

import static com.google.common.base.Predicates.in;
import static com.google.common.base.Predicates.not;
import static com.google.common.collect.FluentIterable.from;
import static java.util.Arrays.asList;
import static org.junit.Assert.fail;
import static org.sonar.ce.task.projectanalysis.measure.Measure.newMeasureBuilder;
import static org.sonar.ce.task.projectanalysis.measure.Measure.Level.ERROR;
import static org.sonar.ce.task.projectanalysis.measure.Measure.Level.OK;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType.BOOL;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType.DATA;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType.FLOAT;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType.INT;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType.LEVEL;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType.PERCENT;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType.RATING;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType.STRING;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType.WORK_DUR;
import static org.sonar.ce.task.projectanalysis.metric.Metric.MetricType.values;
import static org.sonar.ce.task.projectanalysis.qualitygate.Condition.Operator.EQUALS;
import static org.sonar.ce.task.projectanalysis.qualitygate.Condition.Operator.GREATER_THAN;
import static org.sonar.ce.task.projectanalysis.qualitygate.Condition.Operator.LESS_THAN;
import static org.sonar.ce.task.projectanalysis.qualitygate.Condition.Operator.NOT_EQUALS;
import static org.sonar.ce.task.projectanalysis.qualitygate.EvaluationResultAssert.assertThat;

@RunWith(DataProviderRunner.class)
public class ConditionEvaluatorTest {
  @Rule
  public ExpectedException expectedException = ExpectedException.none();

  private ConditionEvaluator underTest = new ConditionEvaluator();

  @Test
  public void test_input_numbers() {
    try {
      Metric metric = createMetric(FLOAT);
      Measure measure = newMeasureBuilder().create(10.2d, 1, null);
      underTest.evaluate(createErrorCondition(metric, LESS_THAN, "20"), measure);
    } catch (NumberFormatException ex) {
      fail();
    }

    try {
      Metric metric = createMetric(INT);
      Measure measure = newMeasureBuilder().create(5, null);
      underTest.evaluate(createErrorCondition(metric, LESS_THAN, "20.1"), measure);
    } catch (NumberFormatException ex) {
      fail();
    }

    try {
      Metric metric = createMetric(PERCENT);
      Measure measure = newMeasureBuilder().create(10.2d, 1, null);
      underTest.evaluate(createErrorCondition(metric, LESS_THAN, "20.1"), measure);
    } catch (NumberFormatException ex) {
      fail();
    }
  }

  @Test
  public void testEquals_for_double() {
    Metric metric = createMetric(FLOAT);
    Measure measure = newMeasureBuilder().create(10.2d, 1, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "10.2"), measure)).hasLevel(ERROR).hasValue(10.2d);
    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "10.1"), measure)).hasLevel(OK).hasValue(10.2d);
  }

  @Test
  public void testEquals_for_String() {
    Metric metric = createMetric(STRING);
    Measure measure = newMeasureBuilder().create("TEST");

    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "TEST"), measure)).hasLevel(ERROR).hasValue("TEST");
    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "TEST2"), measure)).hasLevel(OK).hasValue("TEST");
  }

  @Test
  public void testNotEquals_for_double() {
    Metric metric = createMetric(FLOAT);
    Measure measure = newMeasureBuilder().create(10.2d, 1, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, NOT_EQUALS, "10.2"), measure)).hasLevel(OK).hasValue(10.2d);
    assertThat(underTest.evaluate(createErrorCondition(metric, NOT_EQUALS, "10.1"), measure)).hasLevel(ERROR).hasValue(10.2d);
  }

  @Test
  public void testNotEquals() {
    Metric metric = createMetric(STRING);
    Measure measure = newMeasureBuilder().create("TEST");

    assertThat(underTest.evaluate(createErrorCondition(metric, NOT_EQUALS, "TEST"), measure)).hasLevel(OK).hasValue("TEST");
    assertThat(underTest.evaluate(createErrorCondition(metric, NOT_EQUALS, "TEST2"), measure)).hasLevel(ERROR).hasValue("TEST");
  }

  @Test
  public void testGreater() {
    Metric metric = createMetric(FLOAT);
    Measure measure = newMeasureBuilder().create(10.2d, 1, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, GREATER_THAN, "10.1"), measure)).hasLevel(ERROR).hasValue(10.2d);
    assertThat(underTest.evaluate(createErrorCondition(metric, GREATER_THAN, "10.2"), measure)).hasLevel(OK).hasValue(10.2d);
    assertThat(underTest.evaluate(createErrorCondition(metric, GREATER_THAN, "10.3"), measure)).hasLevel(OK).hasValue(10.2d);
  }

  @Test
  public void testSmaller() {
    Metric metric = createMetric(FLOAT);
    Measure measure = newMeasureBuilder().create(10.2d, 1, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, LESS_THAN, "10.1"), measure)).hasLevel(OK).hasValue(10.2d);
    assertThat(underTest.evaluate(createErrorCondition(metric, LESS_THAN, "10.2"), measure)).hasLevel(OK).hasValue(10.2d);
    assertThat(underTest.evaluate(createErrorCondition(metric, LESS_THAN, "10.3"), measure)).hasLevel(ERROR).hasValue(10.2d);
  }

  @Test
  public void testEquals_Percent() {
    Metric metric = createMetric(PERCENT);
    Measure measure = newMeasureBuilder().create(10.2d, 1, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "10.2"), measure)).hasLevel(ERROR).hasValue(10.2d);
  }

  @Test
  public void testEquals_Float() {
    Metric metric = createMetric(PERCENT);
    Measure measure = newMeasureBuilder().create(10.2d, 1, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "10.2"), measure)).hasLevel(ERROR).hasValue(10.2d);
  }

  @Test
  public void testEquals_Int() {
    Metric metric = createMetric(INT);
    Measure measure = newMeasureBuilder().create(10, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "10"), measure)).hasLevel(ERROR).hasValue(10);
    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "10.2"), measure)).hasLevel(ERROR).hasValue(10);
  }

  @Test
  public void testEquals_Level() {
    Metric metric = createMetric(LEVEL);
    Measure measure = newMeasureBuilder().create(ERROR);

    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, ERROR.name()), measure)).hasLevel(ERROR).hasValue(ERROR.name());

    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, OK.name()), measure)).hasLevel(OK).hasValue(ERROR.name());
  }

  @Test
  public void testNotEquals_Level() {
    Metric metric = createMetric(LEVEL);
    Measure measure = newMeasureBuilder().create(ERROR);

    assertThat(underTest.evaluate(createErrorCondition(metric, NOT_EQUALS, OK.name()), measure)).hasLevel(ERROR).hasValue(ERROR.name());
  }

  @Test
  public void testEquals_BOOL() {
    Metric metric = createMetric(BOOL);
    Measure measure = newMeasureBuilder().create(false, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "1"), measure)).hasLevel(OK).hasValue(false);
    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "0"), measure)).hasLevel(ERROR).hasValue(false);
  }

  @Test
  public void testNotEquals_BOOL() {
    Metric metric = createMetric(BOOL);
    Measure measure = newMeasureBuilder().create(false, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, NOT_EQUALS, "1"), measure)).hasLevel(ERROR).hasValue(false);
    assertThat(underTest.evaluate(createErrorCondition(metric, NOT_EQUALS, "0"), measure)).hasLevel(OK).hasValue(false);
  }

  @Test
  public void getLevel_throws_IEA_if_error_threshold_is_not_parsable_boolean() {
    Metric metric = createMetric(BOOL);
    Measure measure = newMeasureBuilder().create(false, null);

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Quality Gate: Unable to parse value 'polop' to compare against name");

    underTest.evaluate(createErrorCondition(metric, EQUALS, "polop"), measure);
  }

  @Test
  public void testEquals_work_duration() {
    Metric metric = createMetric(WORK_DUR);
    Measure measure = newMeasureBuilder().create(60l, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "60"), measure)).hasLevel(ERROR);
  }

  @Test
  public void getLevel_throws_IEA_if_error_threshold_is_not_parsable_long() {
    Metric metric = createMetric(WORK_DUR);
    Measure measure = newMeasureBuilder().create(60l, null);

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Quality Gate: Unable to parse value 'polop' to compare against name");

    underTest.evaluate(createErrorCondition(metric, EQUALS, "polop"), measure);
  }

  @Test
  public void testErrorLevel() {
    Metric metric = createMetric(FLOAT);
    Measure measure = newMeasureBuilder().create(10.2d, 1, null);

    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "10.2"), measure)).hasLevel(ERROR);
    assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "10.1"), measure)).hasLevel(OK);

    assertThat(underTest.evaluate(new Condition(metric, EQUALS.getDbValue(), "10.3"), measure)).hasLevel(Measure.Level.OK);
    assertThat(underTest.evaluate(new Condition(metric, LESS_THAN.getDbValue(), "10.3"), measure)).hasLevel(Measure.Level.ERROR);
  }

  @Test
  public void condition_is_always_ok_when_measure_is_noValue() {
    for (MetricType metricType : from(asList(values())).filter(not(in(ImmutableSet.of(DATA, LEVEL))))) {
      Metric metric = createMetric(metricType);
      Measure measure = newMeasureBuilder().createNoValue();

      assertThat(underTest.evaluate(createErrorCondition(metric, EQUALS, "10.2"), measure)).hasLevel(OK);
    }
  }

  @Test
  public void testUnsupportedType() {
    Metric metric = createMetric(DATA);
    Measure measure = newMeasureBuilder().create("3.14159265358");

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Conditions on MetricType DATA are not supported");

    underTest.evaluate(createErrorCondition(metric, EQUALS, "1.60217657"), measure);
  }

  @Test
  @UseDataProvider("numericNewMetricTypes")
  public void test_condition_on_numeric_new_metric(MetricType metricType) {
    Metric metric = createNewMetric(metricType);
    Measure measure = newMeasureBuilder().setVariation(3d).createNoValue();

    assertThat(underTest.evaluate(new Condition(metric, GREATER_THAN.getDbValue(), "3"), measure)).hasLevel(OK);
    assertThat(underTest.evaluate(new Condition(metric, GREATER_THAN.getDbValue(), "2"), measure)).hasLevel(ERROR);
  }

  @Test
  @UseDataProvider("numericNewMetricTypes")
  public void condition_on_new_metric_without_value_is_OK(MetricType metricType) {
    Metric metric = createNewMetric(metricType);
    Measure measure = newMeasureBuilder().createNoValue();

    assertThat(underTest.evaluate(new Condition(metric, GREATER_THAN.getDbValue(), "3"), measure)).hasLevel(OK).hasValue(null);
  }

  @DataProvider
  public static Object[][] numericNewMetricTypes() {
    return new Object[][] {
      {FLOAT},
      {INT},
      {WORK_DUR},
    };
  }

  @Test
  @UseDataProvider("unsupportedNewMetricTypes")
  public void condition_on_new_metric_with_unsupported_type(MetricType metricType) {
    Metric metric = createNewMetric(metricType);
    Measure measure = newMeasureBuilder().setVariation(0d).createNoValue();

    expectedException.expect(IllegalArgumentException.class);
    expectedException.expectMessage("Unsupported metric type " + metricType);

    underTest.evaluate(new Condition(metric, EQUALS.getDbValue(), "3"), measure);
  }

  @DataProvider
  public static Object[][] unsupportedNewMetricTypes() {
    return new Object[][] {
      {STRING},
      {LEVEL},
    };
  }

  @Test
  public void test_condition_on_rating() {
    Metric metric = createMetric(RATING);
    Measure measure = newMeasureBuilder().create(4, "D");

    assertThat(underTest.evaluate(new Condition(metric, GREATER_THAN.getDbValue(), "4"), measure)).hasLevel(OK).hasValue(4);
    assertThat(underTest.evaluate(new Condition(metric, GREATER_THAN.getDbValue(), "2"), measure)).hasLevel(ERROR).hasValue(4);
  }

  private static Condition createErrorCondition(Metric metric, Condition.Operator operator, String errorThreshold) {
    return new Condition(metric, operator.getDbValue(), errorThreshold);
  }

  private static MetricImpl createMetric(MetricType metricType) {
    return new MetricImpl(1, "key", "name", metricType);
  }

  private static MetricImpl createNewMetric(MetricType metricType) {
    return new MetricImpl(1, "new_key", "name", metricType);
  }
}
