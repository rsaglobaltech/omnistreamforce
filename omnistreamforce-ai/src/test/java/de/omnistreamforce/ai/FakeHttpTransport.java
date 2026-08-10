package de.omnistreamforce.ai;

import de.omnistreamforce.ai.http.HttpTransport;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * Transporte de mentira: sirve respuestas preparadas y guarda lo que se le pidio.
 */
class FakeHttpTransport implements HttpTransport {

    record Call(String url, String body, Map<String, String> headers) {
    }

    final List<Call> calls = new ArrayList<>();
    private final Deque<Object> responses = new ArrayDeque<>();

    FakeHttpTransport respondWith(int status, String body) {
        responses.add(new Response(status, body));
        return this;
    }

    FakeHttpTransport failWith(RuntimeException error) {
        responses.add(error);
        return this;
    }

    @Override
    public Response post(String url, String body, Map<String, String> headers) {
        calls.add(new Call(url, body, headers));
        if (responses.isEmpty()) {
            return new Response(200, "{}");
        }
        Object next = responses.size() == 1 ? responses.peek() : responses.poll();
        if (next instanceof RuntimeException error) {
            throw error;
        }
        return (Response) next;
    }
}
