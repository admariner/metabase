import { useEffect, useMemo, useRef } from "react";

import type { MetabaseAuthConfig } from "embedding-sdk";
import { printUsageProblemToConsole } from "embedding-sdk/lib/print-usage-problem";
import { getSdkUsageProblem } from "embedding-sdk/lib/usage-problem";
import { useSdkDispatch, useSdkSelector } from "embedding-sdk/store";
import { setUsageProblem } from "embedding-sdk/store/reducer";
import { useSetting } from "metabase/common/hooks";
import { EMBEDDING_SDK_CONFIG } from "metabase/embedding-sdk/config";
import { getTokenFeature } from "metabase/setup/selectors";

export function useSdkUsageProblem({
  authConfig,
  allowConsoleLog = true,
}: {
  authConfig: MetabaseAuthConfig;
  allowConsoleLog?: boolean;
}) {
  const hasLoggedRef = useRef(false);

  const dispatch = useSdkDispatch();

  // When the setting haven't been loaded or failed to query, we assume that the
  // feature is _enabled_ first. Otherwise, when a user's instance is temporarily down,
  // their customer would see an alarming error message on production.
  const isEnabled =
    useSetting(EMBEDDING_SDK_CONFIG.enableEmbeddingSettingKey) ?? true;

  const hasTokenFeature = useSdkSelector((state) => {
    // We also assume that the feature is enabled if the token-features are missing.
    // Same reason as above.
    if (!state.settings.values?.["token-features"]) {
      return true;
    }

    return getTokenFeature(state, EMBEDDING_SDK_CONFIG.tokenFeatureKey);
  });

  const isDevelopmentMode = useSdkSelector((state) => {
    // Assume that we are not in development mode until the setting is loaded
    if (!state.settings.values?.["token-features"]) {
      return false;
    }

    return getTokenFeature(state, "development_mode");
  });

  const usageProblem = useMemo(() => {
    return getSdkUsageProblem({
      authConfig,
      hasTokenFeature,
      isEnabled,
      isDevelopmentMode,
    });
  }, [authConfig, hasTokenFeature, isEnabled, isDevelopmentMode]);

  useEffect(() => {
    // SDK components will stop rendering if a license error is detected.
    dispatch(setUsageProblem(usageProblem));

    // Log the problem to the console once.
    if (!hasLoggedRef.current && allowConsoleLog) {
      printUsageProblemToConsole(usageProblem);
      hasLoggedRef.current = true;
    }
  }, [usageProblem, allowConsoleLog, dispatch]);

  return usageProblem;
}
