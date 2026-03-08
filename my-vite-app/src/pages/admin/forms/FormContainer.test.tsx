import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import FormContainer from './FormContainer';

describe('FormContainer', () => {
  it('renders children', () => {
    render(
      <FormContainer>
        <div>child</div>
      </FormContainer>,
    );
    expect(screen.getByText('child')).not.toBeNull();
  });
});

