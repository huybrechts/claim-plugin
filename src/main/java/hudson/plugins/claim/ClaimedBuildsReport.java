package hudson.plugins.claim;

import hudson.Extension;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.Api;
import hudson.model.Job;
import hudson.model.RootAction;
import hudson.model.Run;
import hudson.model.TransientViewActionFactory;
import hudson.model.View;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestResultAction;
import hudson.util.RunList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import jenkins.model.Jenkins;

import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean(defaultVisibility = 9)
public class ClaimedBuildsReport implements Action {

//    @Extension
    public static class RootClaimedBuildsReport extends ClaimedBuildsReport implements RootAction {
        public String getIconFileName() {
            return null;
        }
    }

    public ClaimedBuildsReport() {
    }

    public String getIconFileName() {
        return "/plugin/claim/icons/claim-24x24.png";
    }

    public String getUrlName() {
        return "claims";
    }

    public static Run getFirstFail(Run r) {
        Run lastGood = r.getPreviousNotFailedBuild();
        Run firstFail;
        if (lastGood == null) {
            firstFail = r.getParent().getFirstBuild();
        } else {
            firstFail = lastGood.getNextBuild();
        }
        return firstFail;
    }

    public String getClaimantText(Run r) {
        ClaimBuildAction claim = r.getAction(ClaimBuildAction.class);
        if (claim == null || !claim.isClaimed()) {
            return Messages.ClaimedBuildsReport_ClaimantText_unclimed();
        }
        String reason = claim.getReason();
        if (reason != null) {
            return Messages.ClaimedBuildsReport_ClaimantText_claimedWithReason(
                    claim.getClaimedBy(), claim.getReason(), claim.getAssignedBy());
        } else {
            return Messages.ClaimedBuildsReport_ClaimantText_claimed(claim
                    .getClaimedBy(), claim.getAssignedBy());
        }
    }

    public View getOwner() {
        View view = Stapler.getCurrentRequest().findAncestorObject(View.class);
        if (view != null) {
            return view;
        } else {
            return Jenkins.getInstance().getStaplerFallback();
        }
    }

    public RunList getBuilds() {
        List<Run> lastBuilds = new ArrayList<Run>();
        for (AbstractProject job : Jenkins.getInstance().getAllItems(AbstractProject.class)) {
            Run lb = job.getLastBuild();
            while (lb != null && (lb.hasntStartedYet() || lb.isBuilding()))
                lb = lb.getPreviousBuild();

            if (lb != null && lb.getAction(ClaimBuildAction.class) != null) {
                lastBuilds.add(lb);
            }
        }

        return RunList.fromRuns(lastBuilds).failureOnly();
    }

    public String getDisplayName() {
        return Messages.ClaimedBuildsReport_DisplayName();
    }

    @Exported(name = "build")
    public List<ClaimReportEntry> getEntries() {
        List<ClaimReportEntry> entries = new ArrayList<ClaimedBuildsReport.ClaimReportEntry>();
        for (Run r : (List<Run>) getBuilds())
            entries.add(new ClaimReportEntry(r));
        return entries;
    }

    public Api getApi() {
        return new Api(this);
    }

    @Extension
    public static class ClaimViewActionFactory extends TransientViewActionFactory {

        @Override
        public List<Action> createFor(View v) {
            return Collections.<Action>singletonList(new ClaimedBuildsReport());
        }

    }

    @ExportedBean(defaultVisibility = 9)
    public static class ClaimReportEntry {
        private Run<?, ?> run;

        public ClaimReportEntry(Run<?, ?> run) {
            super();
            this.run = run;
        }

        @Exported
        public String getJob() {
            return run.getParent().getName();
        }

        @Exported
        public int getNumber() {
            return run.getNumber();
        }

        @Exported
        public ClaimBuildAction getClaim() {
            return run.getAction(ClaimBuildAction.class);
        }

        @Exported
        public String getFailingSince() {
            Run firstFail = getFirstFail(run);
            return firstFail != null ? firstFail.getTimestampString2() : null;
        }

        @Exported
        public String getResult() {
            return run.getResult().toString();
        }

        @Exported
        public List<?> getFailedTests() {
            TestResultAction action = run.getAction(TestResultAction.class);
            if (action == null) return null;

            List<CaseResult> failedTests = action.getFailedTests();
            List<TestEntry> result = new ArrayList<TestEntry>(failedTests.size());
            for (CaseResult cr: failedTests) {
                TestEntry entry = new TestEntry();
                entry.test = cr;
                ClaimTestAction cta = cr.getTestAction(ClaimTestAction.class);
                if (cta != null) {
                    entry.claim = cta;
                }
                result.add(entry);
            }

            return result;

        }

    }

    @ExportedBean(defaultVisibility=9)
    public static class TestEntry {
        ClaimTestAction claim;
        CaseResult test;

        @Exported(inline=true)
        public CaseResult getTest() {
            return test;
        }


        @Exported
        public ClaimTestAction getClaim() {
            return claim;
        }

        @Exported
        public String getUrl() {
            return test.getOwner().getAbsoluteUrl() + "testReport/" + test.getUrl();
        }
    }
}
