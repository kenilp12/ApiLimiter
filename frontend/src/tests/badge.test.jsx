import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import React from 'react';
import { AlgorithmBadge, StatusBadge } from '../components/common/Badge';

describe('AlgorithmBadge Component', () => {
  it('renders Token Bucket badge correctly', () => {
    render(<AlgorithmBadge algorithm="TOKEN_BUCKET" />);
    expect(screen.getByText('Token Bucket')).toBeInTheDocument();
  });

  it('renders Sliding Window badge correctly', () => {
    render(<AlgorithmBadge algorithm="SLIDING_WINDOW" />);
    expect(screen.getByText('Sliding Window')).toBeInTheDocument();
  });

  it('renders Fixed Window badge correctly', () => {
    render(<AlgorithmBadge algorithm="FIXED_WINDOW" />);
    expect(screen.getByText('Fixed Window')).toBeInTheDocument();
  });

  it('renders Leaky Bucket badge correctly', () => {
    render(<AlgorithmBadge algorithm="LEAKY_BUCKET" />);
    expect(screen.getByText('Leaky Bucket')).toBeInTheDocument();
  });
});

describe('StatusBadge Component', () => {
  it('renders Active status badge', () => {
    render(<StatusBadge status="ACTIVE" />);
    expect(screen.getByText('Active')).toBeInTheDocument();
  });

  it('renders Revoked status badge', () => {
    render(<StatusBadge status="REVOKED" />);
    expect(screen.getByText('Revoked')).toBeInTheDocument();
  });
});
