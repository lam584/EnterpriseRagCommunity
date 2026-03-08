import { describe, expect, it } from 'vitest';
import { render, screen } from '@testing-library/react';
import VectorIndexForm from './index.tsx';

describe('VectorIndexForm', () => {
  it('renders basic controls', () => {
    render(<VectorIndexForm />);
    expect(screen.getByText('向量索引构建')).not.toBeNull();
    expect(screen.getByPlaceholderText('数据集路径或名称')).not.toBeNull();
    expect(screen.getByText('构建')).not.toBeNull();
  });
});
