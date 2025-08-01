import userEvent from "@testing-library/user-event";
import type { Location } from "history";
import {
  type InjectedRouter,
  Link,
  Route,
  type WithRouterProps,
  withRouter,
} from "react-router";

import { renderWithProviders, screen } from "__support__/ui";
import { INPUT_WRAPPER_TEST_ID } from "metabase/common/components/TabButton";
import { getDefaultTab, resetTempTabId } from "metabase/dashboard/actions";
import { useDashboardUrlQuery } from "metabase/dashboard/hooks/use-dashboard-url-query";
import { getSelectedTabId } from "metabase/dashboard/selectors";
import { createTabSlug } from "metabase/dashboard/utils";
import { useSelector } from "metabase/lib/redux";
import { MockDashboardContext } from "metabase/public/containers/PublicOrEmbeddedDashboard/mock-context";
import { TEST_CARD } from "metabase/query_builder/containers/test-utils";
import type { DashboardTab } from "metabase-types/api";
import { createMockDashboardCard } from "metabase-types/api/mocks/dashboard";
import type { DashboardState, State } from "metabase-types/store";

import { DashboardTabs } from "./DashboardTabs";
import { TEST_DASHBOARD_STATE } from "./test-utils";
import { useDashboardTabs } from "./use-dashboard-tabs";

function setup({
  tabs,
  slug = undefined,
  isEditing = true,
  dashcards,
}: {
  tabs?: DashboardTab[];
  slug?: string | undefined;
  isEditing?: boolean;
  dashcards?: DashboardState["dashcards"];
} = {}) {
  const dashboard: DashboardState = {
    ...TEST_DASHBOARD_STATE,
    dashcards: dashcards ?? TEST_DASHBOARD_STATE.dashcards,
    dashboards: {
      1: {
        ...TEST_DASHBOARD_STATE.dashboards[1],
        tabs: tabs ?? TEST_DASHBOARD_STATE.dashboards[1].tabs,
      },
    },
  };

  const RoutedDashboardComponent = withRouter(
    ({ location }: { location: Location }) => {
      const { selectedTabId } = useDashboardTabs();
      useDashboardUrlQuery(createMockRouter(), location);
      return (
        <>
          <DashboardTabs />
          <span>Selected tab id is {selectedTabId}</span>
          <br />
          <span>Path is {location.pathname + location.search}</span>
          <Link to="/someotherpath">Navigate away</Link>
        </>
      );
    },
  );

  const OtherComponent = () => {
    const selectedTabId = useSelector(getSelectedTabId);

    return (
      <>
        <span>Another route</span>
        <br />
        <span>Selected tab id is {selectedTabId}</span>
      </>
    );
  };

  const { store } = renderWithProviders(
    <>
      <Route
        path="dashboard/:slug(/:tabSlug)"
        component={(props: WithRouterProps) => {
          return (
            <MockDashboardContext
              dashboardId={1}
              dashboard={{
                ...TEST_DASHBOARD_STATE.dashboards[1],
                dashcards: dashcards
                  ? Object.values(dashcards)
                  : TEST_DASHBOARD_STATE.dashboards[1].dashcards.map(
                      (dcId) => TEST_DASHBOARD_STATE.dashcards[dcId],
                    ),
                tabs: tabs ?? TEST_DASHBOARD_STATE.dashboards[1].tabs,
              }}
              navigateToNewCardFromDashboard={null}
              isEditing={isEditing}
            >
              <RoutedDashboardComponent {...props} />
            </MockDashboardContext>
          );
        }}
      />
      <Route path="someotherpath" component={OtherComponent} />
    </>,
    {
      storeInitialState: { dashboard },
      initialRoute: slug ? `/dashboard/1?tab=${slug}` : "/dashboard/1",
      withRouter: true,
    },
  );
  return {
    getDashcards: () =>
      Object.values((store.getState() as unknown as State).dashboard.dashcards),
  };
}

function queryTab(numOrName: number | string) {
  const name = typeof numOrName === "string" ? numOrName : `Tab ${numOrName}`;
  return screen.queryByRole("tab", { name, hidden: true });
}

async function selectTab(num: number) {
  const selectedTab = queryTab(num) as HTMLElement;
  await userEvent.click(selectedTab);
  return selectedTab;
}

async function createNewTab() {
  await userEvent.click(screen.getByLabelText("Create new tab"));
}

async function openTabMenu(num: number) {
  const dropdownIcons = screen.getAllByRole("img", {
    name: "chevrondown icon",
    hidden: true,
  });
  await userEvent.click(dropdownIcons[num - 1]);
  await screen.findByRole("option");
}

async function selectTabMenuItem(
  num: number,
  name: "Delete" | "Rename" | "Duplicate",
) {
  const dropdownIcons = screen.getAllByRole("img", {
    name: "chevrondown icon",
    hidden: true,
  });
  await userEvent.click(dropdownIcons[num - 1]);
  await userEvent.click(
    await screen.findByRole("option", { name, hidden: true }),
  );
}

async function deleteTab(num: number) {
  return selectTabMenuItem(num, "Delete");
}

async function renameTab(num: number, name: string) {
  await selectTabMenuItem(num, "Rename");

  const inputEl = screen.getByRole("textbox", {
    name: `Tab ${num}`,
    hidden: true,
  });
  await userEvent.clear(inputEl);
  await userEvent.type(inputEl, `${name}{enter}`);
}

async function duplicateTab(num: number) {
  return selectTabMenuItem(num, "Duplicate");
}

async function findSlug({ tabId, name }: { tabId: number; name: string }) {
  return screen.findByText(new RegExp(createTabSlug({ id: tabId, name })));
}

function createMockRouter(): InjectedRouter {
  return {
    push: jest.fn(),
    replace: jest.fn(),
    go: jest.fn(),
    goBack: jest.fn(),
    goForward: jest.fn(),
    setRouteLeaveHook: jest.fn(),
    createPath: jest.fn(),
    createHref: jest.fn(),
    isActive: jest.fn(),
    // @ts-expect-error missing type definition
    listen: jest.fn().mockReturnValue(jest.fn()),
  };
}

describe("DashboardTabs", () => {
  beforeEach(() => {
    resetTempTabId();
  });

  describe("when not editing", () => {
    it("should display tabs without menus when there are two or more", () => {
      setup({ isEditing: false });

      expect(queryTab(1)).toBeVisible();
      expect(queryTab(2)).toBeVisible();
    });

    it("should not display tabs when there is one", () => {
      setup({
        isEditing: false,
        tabs: [getDefaultTab({ tabId: 1, dashId: 1, name: "Tab 1" })],
      });

      expect(queryTab(1)).not.toBeInTheDocument();
      expect(screen.getByText("Path is /dashboard/1")).toBeInTheDocument();
    });

    it("should not display tabs when there are none", () => {
      setup({
        isEditing: false,
        tabs: [],
      });

      expect(queryTab(1)).not.toBeInTheDocument();
      expect(screen.getByText("Path is /dashboard/1")).toBeInTheDocument();
    });

    describe("when selecting tabs", () => {
      it("should automatically select the first tab if no slug is provided", async () => {
        setup({ isEditing: false });

        expect(queryTab(1)).toHaveAttribute("aria-selected", "true");
        expect(queryTab(2)).toHaveAttribute("aria-selected", "false");
        expect(queryTab(3)).toHaveAttribute("aria-selected", "false");

        expect(await findSlug({ tabId: 1, name: "Tab 1" })).toBeInTheDocument();
      });

      it("should automatically select the tab in the slug if valid", async () => {
        setup({
          isEditing: false,
          slug: createTabSlug({ id: 2, name: "Tab 2" }),
        });

        expect(await selectTab(2)).toHaveAttribute("aria-selected", "true");
        expect(queryTab(1)).toHaveAttribute("aria-selected", "false");
        expect(queryTab(3)).toHaveAttribute("aria-selected", "false");

        expect(await findSlug({ tabId: 2, name: "Tab 2" })).toBeInTheDocument();
      });

      it("should automatically select the first tab if slug is invalid", async () => {
        setup({
          isEditing: false,
          slug: createTabSlug({ id: 99, name: "A bad slug" }),
        });

        expect(queryTab(1)).toHaveAttribute("aria-selected", "true");
        expect(queryTab(2)).toHaveAttribute("aria-selected", "false");
        expect(queryTab(3)).toHaveAttribute("aria-selected", "false");

        expect(await findSlug({ tabId: 1, name: "Tab 1" })).toBeInTheDocument();
      });

      it("should allow you to click to select tabs", async () => {
        setup({ isEditing: false });

        expect(await selectTab(2)).toHaveAttribute("aria-selected", "true");
        expect(queryTab(1)).toHaveAttribute("aria-selected", "false");
        expect(queryTab(3)).toHaveAttribute("aria-selected", "false");

        expect(await findSlug({ tabId: 2, name: "Tab 2" })).toBeInTheDocument();
      });
    });
  });

  describe("when editing", () => {
    it("should display a placeholder tab when there are none", async () => {
      setup({ tabs: [] });

      expect(queryTab("Tab 1")).toBeInTheDocument();

      await openTabMenu(1);
      expect(screen.getByText("Duplicate")).toBeInTheDocument();

      expect(screen.getByText("Path is /dashboard/1")).toBeInTheDocument();
    });

    it("should display a placeholder tab when there is only one", async () => {
      setup({
        tabs: [getDefaultTab({ tabId: 1, dashId: 1, name: "Lonely tab" })],
      });

      expect(queryTab("Lonely tab")).toBeInTheDocument();

      await openTabMenu(1);
      expect(screen.getByText("Duplicate")).toBeInTheDocument();

      expect(screen.getByText("Path is /dashboard/1")).toBeInTheDocument();
    });

    it("should allow you to click to select tabs", async () => {
      setup();

      expect(await selectTab(2)).toHaveAttribute("aria-selected", "true");
      expect(queryTab(1)).toHaveAttribute("aria-selected", "false");
      expect(queryTab(3)).toHaveAttribute("aria-selected", "false");

      expect(await findSlug({ tabId: 2, name: "Tab 2" })).toBeInTheDocument();
    });

    describe("when adding tabs", () => {
      it("should add two tabs, assign cards to the first, and select the second when adding a tab for the first time", async () => {
        const { getDashcards } = setup({ tabs: [] });
        await createNewTab();
        const firstNewTab = queryTab(1);
        const secondNewTab = queryTab(2);

        expect(firstNewTab).toBeVisible();
        expect(secondNewTab).toBeVisible();

        expect(firstNewTab).toHaveAttribute("aria-selected", "false");
        expect(secondNewTab).toHaveAttribute("aria-selected", "true");

        const dashcards = getDashcards();
        expect(dashcards[0].dashboard_tab_id).toEqual(-1);
        expect(dashcards[1].dashboard_tab_id).toEqual(-1);
      });

      it("should add add one tab, not reassign cards, and select the tab when adding an additional tab", async () => {
        const { getDashcards } = setup();
        await createNewTab();
        const newTab = queryTab(4);

        expect(newTab).toBeVisible();

        expect(queryTab(1)).toHaveAttribute("aria-selected", "false");
        expect(queryTab(2)).toHaveAttribute("aria-selected", "false");
        expect(queryTab(3)).toHaveAttribute("aria-selected", "false");
        expect(newTab).toHaveAttribute("aria-selected", "true");

        const dashcards = getDashcards();
        expect(dashcards[0].dashboard_tab_id).toEqual(1);
        expect(dashcards[1].dashboard_tab_id).toEqual(2);
      });
    });

    describe("when deleting tabs", () => {
      it("should delete the tab and its cards after clicking `Delete` in the menu", async () => {
        const { getDashcards } = setup();
        await deleteTab(2);

        expect(queryTab(2)).not.toBeInTheDocument();

        const dashcards = getDashcards();
        expect(dashcards[0].isRemoved).toEqual(undefined);
        expect(dashcards[1].isRemoved).toEqual(true);
      });

      it("should select the tab to the left if the selected tab was deleted", async () => {
        setup();
        await selectTab(2);
        await deleteTab(2);

        expect(queryTab(1)).toHaveAttribute("aria-selected", "true");
        expect(await findSlug({ tabId: 1, name: "Tab 1" })).toBeInTheDocument();
      });

      it("should select the tab to the right if the selected tab was deleted and was the first tab", async () => {
        setup();
        await selectTab(1);
        await deleteTab(1);

        expect(queryTab(2)).toHaveAttribute("aria-selected", "true");
        expect(await findSlug({ tabId: 2, name: "Tab 2" })).toBeInTheDocument();
      });

      it("should keep the last tab and remove slug if the penultimate tab was deleted", async () => {
        setup();
        await deleteTab(3);

        expect(await findSlug({ tabId: 1, name: "Tab 1" })).toBeInTheDocument();

        await deleteTab(2);

        expect(queryTab(1)).toBeInTheDocument();
        await openTabMenu(1);
        expect(screen.getByText("Duplicate")).toBeInTheDocument();

        expect(screen.getByText("Path is /dashboard/1")).toBeInTheDocument();
      });

      it("should correctly update selected tab id when deleting tabs (#30923)", async () => {
        setup({ tabs: [] });
        await createNewTab();
        await createNewTab();

        await deleteTab(2);
        await deleteTab(2);

        expect(screen.getByText("Selected tab id is -1")).toBeInTheDocument();
      });

      it("should show confirmation modal when deleting a tab with all dashboard questions", async () => {
        setup({
          dashcards: {
            1: createMockDashboardCard({
              id: 1,
              dashboard_id: 1,
              dashboard_tab_id: 2,
              card: {
                ...TEST_CARD,
                id: 1,
                dashboard_id: 1, // Dashboard question
              },
            }),
            2: createMockDashboardCard({
              id: 2,
              dashboard_id: 1,
              dashboard_tab_id: 2,
              card: {
                ...TEST_CARD,
                id: 2,
                dashboard_id: 1, // Dashboard question
              },
            }),
          },
        });
        await deleteTab(2);

        expect(screen.getByRole("dialog")).toBeInTheDocument();
        expect(
          screen.getByText("Delete this tab and its charts?"),
        ).toBeInTheDocument();

        await userEvent.click(
          screen.getByRole("button", { name: "Delete tab" }),
        );

        expect(queryTab(2)).not.toBeInTheDocument();
      });

      it("should show confirmation modal with a list of the affected questions when deleting a tab with a mix of saved and dashboard questions", async () => {
        setup({
          dashcards: {
            1: createMockDashboardCard({
              id: 1,
              dashboard_id: 1,
              dashboard_tab_id: 2,
              card: {
                ...TEST_CARD,
                id: 1,
                name: "Dashboard question",
                dashboard_id: 1,
              },
            }),
            2: createMockDashboardCard({
              id: 2,
              dashboard_id: 1,
              dashboard_tab_id: 2,
              card: {
                ...TEST_CARD,
                id: 2,
                name: "Saved question",
                dashboard_id: null,
              },
            }),
          },
        });

        await deleteTab(2);

        expect(screen.getByRole("dialog")).toBeInTheDocument();
        expect(screen.getByText("Dashboard question")).toBeInTheDocument();
        expect(screen.queryByText("Saved question")).not.toBeInTheDocument();
        expect(screen.getByText("Delete this tab?")).toBeInTheDocument();

        await userEvent.click(
          screen.getByRole("button", { name: "Delete tab" }),
        );

        expect(queryTab(2)).not.toBeInTheDocument();
      });
    });

    describe("when renaming tabs", () => {
      it("should allow the user to rename the tab after clicking `Rename` in the menu", async () => {
        setup();
        const name = "A cool new name";
        await renameTab(1, name);

        expect(queryTab(name)).toBeInTheDocument();
        expect(await findSlug({ tabId: 1, name })).toBeInTheDocument();
      });

      it("should allow renaming via double click", async () => {
        setup();
        const name = "Another cool new name";
        const inputWrapperEl = screen.getAllByTestId(INPUT_WRAPPER_TEST_ID)[0];
        await userEvent.dblClick(inputWrapperEl);

        const inputEl = screen.getByRole("textbox", {
          name: "Tab 1",
          hidden: true,
        });
        await userEvent.clear(inputEl);
        await userEvent.type(inputEl, `${name}{enter}`);

        expect(queryTab(name)).toBeInTheDocument();
        expect(await findSlug({ tabId: 1, name })).toBeInTheDocument();
      });
    });
  });

  describe("when duplicating tabs", () => {
    it("should allow the user to duplicate the placeholder tab if there are none", async () => {
      setup({ tabs: [] });

      await duplicateTab(1);

      expect(queryTab("Tab 1")).toBeInTheDocument();
      expect(queryTab("Copy of Tab 1")).toBeInTheDocument();

      expect(screen.getByText("Selected tab id is -2")).toBeInTheDocument();
      expect(screen.getByText("Path is /dashboard/1")).toBeInTheDocument();
    });

    it("should allow the user to duplicate a tab", async () => {
      setup();

      await duplicateTab(1);

      expect(queryTab("Copy of Tab 1")).toBeInTheDocument();

      expect(screen.getByText("Selected tab id is -2")).toBeInTheDocument();
      expect(screen.getByText("Path is /dashboard/1")).toBeInTheDocument();
    });
  });

  describe("when navigating away from dashboard", () => {
    it("should preserve selected tab id", async () => {
      setup();

      await selectTab(2);
      expect(screen.getByText("Selected tab id is 2")).toBeInTheDocument();

      await userEvent.click(screen.getByText("Navigate away"));
      expect(screen.getByText("Another route")).toBeInTheDocument();
      expect(screen.getByText("Selected tab id is 2")).toBeInTheDocument();
    });
  });
});
