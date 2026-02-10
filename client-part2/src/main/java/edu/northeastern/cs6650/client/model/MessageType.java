package edu.northeastern.cs6650.client.model;

/**
 * Enumeration of supported chat message types.
 *
 * <p>Message types are used by the server to distinguish between normal chat
 * traffic and lifecycle events such as user join or leave.</p>
 */
public enum MessageType {
  JOIN,
  TEXT,
  LEAVE
}
