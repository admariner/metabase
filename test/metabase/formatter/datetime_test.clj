(ns metabase.formatter.datetime-test
  (:require
   [clojure.test :refer :all]
   [medley.core :as m]
   [metabase.appearance.core :as appearance]
   [metabase.formatter.datetime :as datetime]
   [metabase.models.visualization-settings :as mb.viz]
   [metabase.test :as mt])
  (:import
   (java.util Locale)))

(set! *warn-on-reflection* true)

(def ^:private now "2020-07-16T18:04:00Z[UTC]")

(deftest ^:parallel determine-time-format-test
  (testing "Capture the behaviors of determine-time-format"
    (testing "When :time-enabled is set to nil no time format is returned"
      (is (nil? (#'datetime/determine-time-format {:time-enabled nil}))))
    (testing "When :time-enabled is set to minutes they are shown"
      (is (= "h:mm a" (#'datetime/determine-time-format {:time-enabled "minutes"}))))
    (testing "When :time-enabled is set to seconds they are shown"
      (is (= "h:mm:ss a" (#'datetime/determine-time-format {:time-enabled "seconds"}))))
    (testing "When :time-enabled is set to milliseconds are shown"
      (is (= "h:mm:ss.SSS a" (#'datetime/determine-time-format {:time-enabled "milliseconds"}))))
    (testing "time-style is modified to use the specified time precision"
      (is (= "HH:mm:ss.SSS" (#'datetime/determine-time-format {:time-style "HH:mm"
                                                               :time-enabled "milliseconds"}))))
    (testing "The default behavior when the :time-enabled key is absent is to show minutes"
      (is (= "h:mm a" (#'datetime/determine-time-format {}))))))

(defn- format-temporal-str
  ([timezone-id temporal-str col] (format-temporal-str timezone-id temporal-str col {}))
  ([timezone-id temporal-str col viz-settings]
   ((datetime/make-temporal-str-formatter timezone-id col viz-settings) temporal-str)))

;;; TODO (Cam 6/18/25) -- these fail for me locally with `Dec` instead of `December` -- see
;;; https://metaboat.slack.com/archives/CKZEMT1MJ/p1750288689214359
(deftest format-temporal-str-test
  (mt/with-temporary-setting-values [custom-formatting nil]
    (testing "Null values do not blow up"
      (is (= ""
             (format-temporal-str "UTC" nil :now))))
    (testing "Temporal Units are formatted"
      (testing :minute
        (is (= "July 16, 2020, 6:04 PM"
               (format-temporal-str "UTC" now {:unit :minute}))))
      (testing :hour
        (is (= "July 16, 2020, 6 PM"
               (format-temporal-str "UTC" now {:unit :hour}))))
      (testing :day
        (is (= "Thursday, July 16, 2020"
               (format-temporal-str "UTC" now {:unit :day}))))
      (testing :week
        (is (= "July 16, 2020 - July 22, 2020"
               (format-temporal-str "UTC" now {:unit :week}))))
      (testing :month
        (is (= "July, 2020"
               (format-temporal-str "UTC" now {:unit :month}))))
      (testing :quarter
        (is (= "Q3 - 2020"
               (format-temporal-str "UTC" now {:unit :quarter}))))
      (testing :year
        (is (= "2020"
               (format-temporal-str "UTC" now {:unit :year})))))
    (testing "x-of-y Temporal Units are formatted"
      (testing :minute-of-hour
        (is (= "1st"
               (format-temporal-str "UTC" "1" {:unit :minute-of-hour}))))
      (testing :day-of-month
        (is (= "2nd"
               (format-temporal-str "UTC" "2" {:unit :day-of-month}))))
      (testing :day-of-year
        (is (= "203rd"
               (format-temporal-str "UTC" "203" {:unit :day-of-year}))))
      (testing :week-of-year
        (is (= "44th"
               (format-temporal-str "UTC" "44" {:unit :week-of-year}))))
      (testing :day-of-week
        (is (= "Thursday"
               (format-temporal-str "UTC" "4" {:unit :day-of-week}))))
      (testing :month-of-year
        (is (= "May"
               (format-temporal-str "UTC" "5" {:unit :month-of-year}))))
      (testing :quarter-of-year
        (is (= "Q3"
               (format-temporal-str "UTC" "3" {:unit :quarter-of-year}))))
      (testing :hour-of-day
        (is (= "4 AM"
               (format-temporal-str "UTC" "4" {:unit :hour-of-day})))))
    (testing "Can render time types (#15146)"
      (is (= "8:05 AM"
             (format-temporal-str "UTC" "08:05:06Z"
                                  {:effective_type :type/Time}))))
    (testing "Can render date time types (Part of resolving #36484)"
      (is (= "April 1, 2014, 8:30 AM"
             (format-temporal-str "UTC" "2014-04-01T08:30:00"
                                  {:effective_type :type/DateTime}))))
    (testing "When `:time_enabled` is `nil` the time is truncated for date times."
      (is (= "April 1, 2014"
             (format-temporal-str "UTC" "2014-04-01T08:30:00"
                                  {:effective_type :type/DateTime
                                   :settings       {:time_enabled nil}}))))
    (testing "When `:time_enabled` is `nil` the time is truncated for times (even though this may not make sense)."
      (is (= ""
             (format-temporal-str "UTC" "08:05:06Z"
                                  {:effective_type :type/Time
                                   :settings       {:time_enabled nil}}))))))

(defn- format-with-colname-key
  [column viz]
  (format-temporal-str "UTC" now column {::mb.viz/column-settings
                                         {{::mb.viz/column-name (:name column)} viz}}))

(defn- format-with-field-id-key
  [column viz]
  (format-temporal-str "UTC" now column {::mb.viz/column-settings
                                         {{::mb.viz/field-id (:id column)} viz}}))

(defn- format-with-field-id-and-colname-key
  [column viz]
  (format-temporal-str "UTC" now column {::mb.viz/column-settings
                                         {{::mb.viz/field-id (:id column)} {::mb.viz/view-as nil}
                                          {::mb.viz/column-name (:name column)} viz}}))

(def ^:private formatters-with-viz
  [{:name " with column-name as key"
    :fmt-fn format-with-colname-key}
   {:name " with field-id as key"
    :fmt-fn format-with-field-id-key}
   {:name " with field-id and column name keys present"
    :fmt-fn format-with-field-id-and-colname-key}])

(deftest format-temporal-str-column-viz-settings-written-date-test
  (mt/with-temporary-setting-values [custom-formatting nil]
    (let [column {:field_ref [:field 1 nil] :name "created_at" :id 1 :effective_type :type/Date}]
      (doseq [{:keys [name fmt-fn]} formatters-with-viz]
        (let [fmt-part (partial fmt-fn column)]
          (doseq [[date-style normal-result abbreviated-result]
                  [["MMMM D, YYYY" "July 16, 2020" "Jul 16, 2020"]
                   ["D MMMM, YYYY" "16 July, 2020" "16 Jul, 2020"]
                   ["dddd, MMMM D, YYYY" "Thursday, July 16, 2020" "Thu, Jul 16, 2020"] ;; Render datetimes with Day of Week option. (#27105)
                   [nil "July 16, 2020" "Jul 16, 2020"]]]     ;; Render abbreviated date styles when no other style data is explicitly set. (#27020)
            (testing (str "Date style: " date-style " correctly formats" name)
              (is (= normal-result
                     (fmt-part (when date-style {::mb.viz/date-style date-style})))))
            (testing (str "Date style: " date-style " with abbreviation correctly formats.")
              (is (= abbreviated-result
                     (fmt-part (merge {::mb.viz/date-abbreviate true}
                                      (when date-style {::mb.viz/date-style date-style}))))))))))))

(deftest format-temporal-str-column-viz-settings-numerical-date-test
  (mt/with-temporary-setting-values [custom-formatting nil]
    (let [column {:field_ref [:field 1 nil] :name "created_at" :id 1 :effective_type :type/Date}]
      (doseq [{:keys [name fmt-fn]} formatters-with-viz]
        (let [fmt-part (partial fmt-fn column)]
          (doseq [[date-style slash-result dash-result dot-result]
                  [["M/D/YYYY" "7/16/2020" "7-16-2020" "7.16.2020"]
                   ["D/M/YYYY" "16/7/2020" "16-7-2020" "16.7.2020"]
                   ["YYYY/M/D" "2020/7/16" "2020-7-16" "2020.7.16"]
                   [nil "July 16, 2020" "July 16, 2020" "July 16, 2020"]] ;; nil date-style does not blow up when date-separator exists
                  date-separator ["/" "-" "."]]
            (testing (str "Date style: " date-style " with '" date-separator "' correctly formats" name)
              (is (= (get {"/" slash-result
                           "-" dash-result
                           "." dot-result} date-separator)
                     (fmt-part (merge {::mb.viz/date-separator date-separator}
                                      (when date-style {::mb.viz/date-style date-style})))))))
          (testing "Default date separator is '/'"
            (is (= "7/16/2020"
                   (fmt-part {::mb.viz/date-style "M/D/YYYY"})))))))))

(deftest format-temporal-str-column-viz-settings-custom-fmt-test
  (testing "Custom Formatting options are respected as defaults."
    ;; NOTE - format-temporal-str gets global settings from the `::mb.viz/global-column-settings`
    ;; key of the viz-settings argument. These are looked up based on the type of the column.
    (mt/with-temporary-setting-values [custom-formatting {:type/Temporal {:date_style      "MMMM D, YYYY"
                                                                          :date_abbreviate true}}]
      (let [global-settings (m/map-vals mb.viz/db->norm-column-settings-entries
                                        (appearance/custom-formatting))]
        (is (= "Jul 16, 2020"
               (format-temporal-str "UTC" now
                                    {:effective_type :type/Date}
                                    {::mb.viz/global-column-settings global-settings})))))
    (mt/with-temporary-setting-values [custom-formatting {:type/Temporal {:date_style     "M/DD/YYYY"
                                                                          :date_separator "-"}}]
      (let [global-settings (m/map-vals mb.viz/db->norm-column-settings-entries
                                        (appearance/custom-formatting))]
        (is (= "7-16-2020, 6:04 PM"
               (format-temporal-str
                "UTC"
                now
                {:effective_type :type/DateTime}
                {::mb.viz/global-column-settings global-settings})))))))

(deftest format-datetime-test
  (testing "Testing permutations of a datetime string with different type information and viz settings (#36559)"
    (mt/with-temporary-setting-values [custom-formatting nil]
      (let [common-viz-settings {::mb.viz/column-settings
                                 ;; Settings specific to a certain column
                                 {{::mb.viz/column-name "CUSTOM_DATETIME"}
                                  {::mb.viz/date-style   "dddd, MMMM D, YYYY"
                                   ::mb.viz/time-enabled "milliseconds"
                                   ::mb.viz/time-style   "h:mm a"}}
                                 ;; Global settings
                                 ::mb.viz/global-column-settings
                                 {:type/Temporal {::mb.viz/time-style "HH:mm"}}}
            col                 {:name "DATETIME" :base_type :type/DateTime}
            time-str            "2023-12-11T21:51:57.265914Z"]
        (testing "Global settings are applied to a :type/DateTimeDateTime"
          (is (= "December 11, 2023, 21:51"
                 (format-temporal-str "UTC" time-str col common-viz-settings))))
        (testing "A :type/DateTimeDateTimeWithLocalTZ is a :type/DateTimeDateTime"
          (is (= "December 11, 2023, 21:51"
                 (let [col (assoc col :base_type :type/DateTimeWithLocalTZ)]
                   (format-temporal-str "UTC" time-str col common-viz-settings)))))
        (testing "Custom settings are applied when the column has them"
          ;; Note that the time style of the column setting has precedence over the global setting
          (is (= "Monday, December 11, 2023, 9:51:57.265 PM"
                 (let [col (assoc col :name "CUSTOM_DATETIME")]
                   (format-temporal-str "UTC" time-str col common-viz-settings)))))
        (testing "Column metadata settings are applied"
          (is (= "Dec 11, 2023, 21:51:57"
                 (let [col (assoc col :settings {:time_enabled "seconds"
                                                 :date_abbreviate true})]
                   (format-temporal-str "UTC" time-str col common-viz-settings)))))
        (testing "Various settings can be merged"
          (testing "We abbreviate the base case..."
            (is (= "Dec 11, 2023, 21:51"
                   (let [common-viz-settings (assoc-in common-viz-settings
                                                       [::mb.viz/global-column-settings
                                                        :type/Temporal
                                                        ::mb.viz/date-abbreviate]
                                                       true)]
                     (format-temporal-str "UTC" time-str col common-viz-settings)))))
          (testing "...and we abbreviate the custome column formatting as well"
            (is (= "Mon, Dec 11, 2023, 9:51:57.265 PM"
                   (let [col                 (assoc col :name "CUSTOM_DATETIME")
                         common-viz-settings (assoc-in common-viz-settings
                                                       [::mb.viz/global-column-settings
                                                        :type/Temporal
                                                        ::mb.viz/date-abbreviate]
                                                       true)]
                     (format-temporal-str "UTC" time-str col common-viz-settings))))))
        (testing "The appropriate formatting is applied when the column type is date"
          (is (= "December 11, 2023"
                 (let [col (assoc col :effective_type :type/Date)]
                   (format-temporal-str "UTC" time-str col common-viz-settings)))))
        (testing "The appropriate formatting is applied when the column type is time"
          (is (= "21:51"
                 (let [col (assoc col :effective_type :type/Time)]
                   (format-temporal-str "UTC" time-str col common-viz-settings)))))
        (testing "Formatting works for times with a custom time-enabled"
          (is (= "21:51:57.265"
                 (let [col                 (assoc col :effective_type :type/Time)
                       common-viz-settings (assoc-in common-viz-settings
                                                     [::mb.viz/global-column-settings
                                                      :type/Temporal
                                                      ::mb.viz/time-enabled]
                                                     "milliseconds")]
                   (format-temporal-str "UTC" time-str col common-viz-settings)))))))))

(deftest format-default-unit-test
  (testing "When the unit is :default we use the column type."
    (mt/with-temporary-setting-values [custom-formatting nil]
      (let [col {:unit           :default
                 :effective_type :type/Time
                 :base_type      :type/Time}]
        (is (= "3:30 PM"
               (format-temporal-str "UTC" "15:30:45Z" col nil))))))
  (testing "Corner case: Return the time string when there is no useful information about it _and_ it's not formattable."
    ;; This addresses a rare case (might never happen IRL) in which we try to apply the default formatting of
    ;; "MMMM d, yyyy" to a time, but we don't know it's a time so we error our.
    (mt/with-temporary-setting-values [custom-formatting nil]
      (let [col {:unit           :default}]
        (is (= "15:30:45Z"
               (format-temporal-str "UTC" "15:30:45Z" col nil)))))))

(deftest ^:parallel year-in-dates-near-start-or-end-of-year-is-correct-test
  (testing "When the date is at the start/end of the year, the year is formatted properly. (#40306)"
    ;; Our datetime formatter relies on the `java-time.api`, for which there are many different, sometimes confusing,
    ;; formatter patterns: https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatterBuilder.html#appendPattern-java.lang.String-
    ;; In this case, 'YYYY' is a week-of-year style year, which calculates which week a date falls into before returning the year.
    ;; Sometimes days near the start/end of a year will fall into a week in the wrong year.
    ;; For example, apparently 2023-12-31 falls into the 1st week of 2024, which probably not the year you'd expect to see.
    ;; What we probably do want is 'yyyy' which calculates what day of the year the date is and then returns the year.
    (let [dates (fn [year] [(format "%s-01-01" year) (format "%s-12-31" year)])
          fmt (fn [s]
                (format-temporal-str "UTC" s {:field_ref      [:column_name "created_at"]
                                              :effective_type :type/Date}
                                     {::mb.viz/column-settings
                                      {{::mb.viz/column-name "created_at"} {::mb.viz/date-style "YYYY-MM-dd"}}}))]
      (doseq [the-date (mapcat dates (range 2008 3008))]
        (is (= the-date (fmt the-date)))))))

(deftest site-locale-used-for-formatting-test
  (testing "Datetime formatting uses site-locale and does not modify JVM locale"
    (let [original-jvm-locale (Locale/getDefault)
          test-datetime       "2020-07-16T18:04:00Z[UTC]"
          col                 {:effective_type :type/DateTime}]
      (mt/with-temporary-setting-values [site-locale "de"]
        (let [german-result (format-temporal-str "UTC" test-datetime col)]
          (is (re-find #"Juli" german-result))
          (is (= original-jvm-locale (Locale/getDefault)))))

      (mt/with-temporary-setting-values [site-locale "fr"]
        (let [french-result (format-temporal-str "UTC" test-datetime col)]
          (is (re-find #"juillet" french-result))
          (is (= original-jvm-locale (Locale/getDefault)))))

      (is (= original-jvm-locale (Locale/getDefault))))))
