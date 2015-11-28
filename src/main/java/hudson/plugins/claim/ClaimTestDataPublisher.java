package hudson.plugins.claim;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.Descriptor;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.TaskListener;
import hudson.tasks.junit.CaseResult;
import hudson.tasks.junit.TestAction;
import hudson.tasks.junit.TestDataPublisher;
import hudson.tasks.junit.TestObject;
import hudson.tasks.junit.TestResult;
import hudson.tasks.junit.TestResultAction;
import hudson.tasks.test.AbstractTestResultAction;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClaimTestDataPublisher extends TestDataPublisher {

    @DataBoundConstructor
    public ClaimTestDataPublisher() {}

    @Override
    public Data contributeTestData(Run<?,?> run, @Nonnull FilePath workspace, Launcher launcher,
                                   TaskListener listener, TestResult testResult) {

        AbstractBuild<?,?> build = (AbstractBuild<?, ?>) run;

        Data data = new Data(build);

        for (CaseResult result: testResult.getFailedTests()) {
            try {
                CaseResult previous = null;
                AbstractBuild<?,?> b = build;
                while (previous == null && b != null) {
                    b = b.getPreviousBuild();
                    if (b != null && !b.isBuilding()) {
                        TestResultAction tra = b.getAction(TestResultAction.class);
                        if (tra != null) {
                            previous = (CaseResult) tra.findCorrespondingResult(result.getId());
                        }
                        if (b.getResult().isBetterOrEqualTo(Result.UNSTABLE)) break; //only look until the last unstable build
                    }
                }

                if (previous != null) {
                    ClaimTestAction previousAction = previous.getTestAction(ClaimTestAction.class);
                    if (previousAction != null && previousAction.isClaimed() && previousAction.isSticky()) {
                        ClaimTestAction action = new ClaimTestAction(data, result.getId());
                        previousAction.copyTo(action);
                        data.addClaim(result.getId(), action);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace(listener.error("Failed to create claim for " + result.getFullDisplayName()));
            }
        }

        return data;

    }

    public static class Data extends TestResultAction.Data implements Saveable {

        private Map<String,ClaimTestAction> claims = new HashMap<String,ClaimTestAction>();

        private final AbstractBuild<?,?> build;

        public Data(AbstractBuild<?,?> build) {
            this.build = build;
        }

        public AbstractBuild<?,?> getBuild() {
            return build;
        }
        
        public String getURL() {
            return build.getUrl();
        }

        @Override
        public List<TestAction> getTestAction(TestObject testObject) {
            String id = testObject.getId();
            ClaimTestAction result = claims.get(id);

            // In Hudson 1.347 or so, IDs changed, and a junit/ prefix was added.
            // Attempt to fix this backward-incompatibility
            if (result == null && id.startsWith("junit")) {
                result = claims.get(id.substring(5));
            }

            if (result != null) {
                return Collections.<TestAction>singletonList(result);
            }

            if (testObject instanceof CaseResult) {
                CaseResult cr = (CaseResult) testObject;
                if (!cr.isPassed() && !cr.isSkipped()) {
                    return Collections.<TestAction>singletonList(new ClaimTestAction(this, id));
                }
            }

            return Collections.emptyList();
        }

        public void save() throws IOException {
            build.save();
        }

        public void addClaim(String testObjectId,
                ClaimTestAction claim) {
            claims.put(testObjectId, claim);
        }

    }

    @Extension
    public static class DescriptorImpl extends Descriptor<TestDataPublisher> {

        @Override
        public String getDisplayName() {
            return Messages.ClaimTestDataPublisher_DisplayName();
        }
    }


}
