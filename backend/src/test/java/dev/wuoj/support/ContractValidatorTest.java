package dev.wuoj.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.model.SimpleResponse;
import org.junit.jupiter.api.Test;

/** The contract check itself: correct bodies pass, and wrong or undocumented fields fail. */
class ContractValidatorTest {

    private static boolean valid(String body) {
        var request = SimpleRequest.Builder.get("/api/auth/me").withHeader("Cookie", "SESSION=x").build();
        var response = SimpleResponse.Builder.ok().withContentType("application/json").withBody(body).build();
        return !Contract.VALIDATOR.validate(request, response).hasErrors();
    }

    @Test
    void conformingBodyPasses() {
        assertThat(valid("{\"id\":1,\"username\":\"student\",\"role\":\"STUDENT\"}")).isTrue();
    }

    @Test
    void undocumentedFieldFails() {
        assertThat(valid("{\"id\":1,\"username\":\"student\",\"role\":\"STUDENT\",\"password\":\"x\"}")).isFalse();
    }

    @Test
    void missingRequiredFieldFails() {
        assertThat(valid("{\"id\":1,\"role\":\"STUDENT\"}")).isFalse();
    }

    @Test
    void wrongEnumValueFails() {
        assertThat(valid("{\"id\":1,\"username\":\"student\",\"role\":\"TEACHER\"}")).isFalse();
    }

    @Test
    void everyOperationOfTheContractIsKnown() {
        assertThat(Contract.operationIds()).contains("login", "logout", "getCurrentUser", "listProblems", "getProblem",
                "listSubmissions", "createSubmission", "getSubmission", "streamSubmissionEvents");
    }
}
